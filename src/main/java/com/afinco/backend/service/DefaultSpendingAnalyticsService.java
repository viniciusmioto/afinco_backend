package com.afinco.backend.service;

import com.afinco.backend.api.analytics.dto.CategorySpendingResponse;
import com.afinco.backend.api.analytics.dto.SpendingGrouping;
import com.afinco.backend.api.analytics.dto.SpendingOverviewResponse;
import com.afinco.backend.api.analytics.dto.SpendingPeriodResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.Statement;
import com.afinco.backend.domain.StatementPeriod;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.mapper.CategoryMapper;
import com.afinco.backend.mapper.StatementMapper;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.StatementRepository;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.repository.projection.DailyCategorySpending;
import com.afinco.backend.repository.projection.StatementCategorySpending;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DefaultSpendingAnalyticsService implements SpendingAnalyticsService {

    /** Billing cycles run 28 to 31 days; a shorter statement (such as a card's first) covers a partial cycle. */
    static final int MIN_COMPLETE_STATEMENT_DAYS = 28;

    private final TransactionRepository transactionRepository;
    private final StatementRepository statementRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final StatementMapper statementMapper;
    private final Clock clock;

    @Autowired
    public DefaultSpendingAnalyticsService(
            TransactionRepository transactionRepository,
            StatementRepository statementRepository,
            CategoryRepository categoryRepository,
            CategoryMapper categoryMapper,
            StatementMapper statementMapper) {
        this(transactionRepository, statementRepository, categoryRepository, categoryMapper, statementMapper,
                Clock.systemUTC());
    }

    DefaultSpendingAnalyticsService(
            TransactionRepository transactionRepository,
            StatementRepository statementRepository,
            CategoryRepository categoryRepository,
            CategoryMapper categoryMapper,
            StatementMapper statementMapper,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.statementRepository = statementRepository;
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
        this.statementMapper = statementMapper;
        this.clock = clock;
    }

    @Override
    public SpendingOverviewResponse findSpending(SpendingGrouping groupBy, String bankName) {
        String bank = StringUtils.hasText(bankName) ? bankName.trim() : null;
        if (groupBy == SpendingGrouping.STATEMENT && bank == null) {
            throw new InvalidRequestException("Choose a bank to group spending by statement");
        }

        List<Statement> statements = statementRepository.findAllForBankOldestFirst(bank);
        List<SpendingPeriodResponse> periods = groupBy == SpendingGrouping.STATEMENT
                ? statementPeriods(statements, bank)
                : monthPeriods(statements, bank);
        return new SpendingOverviewResponse(groupBy, bank, spendingCategories(), periods);
    }

    private List<CategoryResponse> spendingCategories() {
        return categoryRepository.findAll(Sort.by("id")).stream()
                .filter(category -> category.getExpenseType() != ExpenseType.PAYMENT)
                .map(categoryMapper::toResponse)
                .toList();
    }

    private List<SpendingPeriodResponse> statementPeriods(List<Statement> statements, String bank) {
        Map<Long, CategoryTotals> totals = new HashMap<>();
        for (StatementCategorySpending row : transactionRepository.sumSpendingByStatementAndCategory(bank)) {
            totals.computeIfAbsent(row.getStatementId(), id -> new CategoryTotals())
                    .add(row.getCategoryId(), row.getTotalAmount(), row.getTransactionCount());
        }

        return statements.stream()
                .map(statement -> {
                    StatementPeriod period = statement.getPeriod();
                    long days = ChronoUnit.DAYS.between(period.startDate(), period.endDate()) + 1;
                    return new SpendingPeriodResponse(
                            String.valueOf(statement.getId()),
                            period.startDate(),
                            period.endDate(),
                            statementMapper.toSummary(statement),
                            days >= MIN_COMPLETE_STATEMENT_DAYS,
                            CategoryTotals.responses(totals.get(statement.getId())));
                })
                .toList();
    }

    /** Every calendar month from the first to the last month with spending or statement coverage, gaps included. */
    private List<SpendingPeriodResponse> monthPeriods(List<Statement> statements, String bank) {
        Map<YearMonth, CategoryTotals> totals = new TreeMap<>();
        for (DailyCategorySpending row : transactionRepository.sumSpendingByDateAndCategory(bank)) {
            totals.computeIfAbsent(YearMonth.from(row.getDate()), month -> new CategoryTotals())
                    .add(row.getCategoryId(), row.getTotalAmount(), row.getTransactionCount());
        }

        StatementCoverage coverage = StatementCoverage.of(statements);
        List<YearMonth> bounds = Stream.of(
                        totals.keySet().stream(),
                        coverage.firstDay().map(YearMonth::from).stream(),
                        coverage.lastDay().map(YearMonth::from).stream())
                .flatMap(stream -> stream)
                .sorted()
                .toList();
        if (bounds.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);
        List<SpendingPeriodResponse> periods = new ArrayList<>();
        for (YearMonth month = bounds.getFirst(); !month.isAfter(bounds.getLast()); month = month.plusMonths(1)) {
            periods.add(new SpendingPeriodResponse(
                    month.toString(),
                    month.atDay(1),
                    month.atEndOfMonth(),
                    null,
                    coverage.isComplete(month, today),
                    CategoryTotals.responses(totals.get(month))));
        }
        return periods;
    }

    /** Running per-category sums for one period. */
    private static final class CategoryTotals {

        private final Map<Long, BigDecimal> amounts = new TreeMap<>();
        private final Map<Long, Long> counts = new HashMap<>();

        void add(Long categoryId, BigDecimal amount, long transactionCount) {
            amounts.merge(categoryId, amount, BigDecimal::add);
            counts.merge(categoryId, transactionCount, Long::sum);
        }

        /** SQLite sums decimals as floating point, so each total is rounded back to cents. */
        static List<CategorySpendingResponse> responses(CategoryTotals totals) {
            return Optional.ofNullable(totals).stream()
                    .flatMap(value -> value.amounts.entrySet().stream()
                            .map(entry -> new CategorySpendingResponse(
                                    entry.getKey(),
                                    entry.getValue().setScale(2, RoundingMode.HALF_UP),
                                    value.counts.get(entry.getKey()))))
                    .toList();
        }
    }
}
