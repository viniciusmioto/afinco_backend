package com.afinco.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.afinco.backend.api.analytics.dto.CategorySpendingResponse;
import com.afinco.backend.api.analytics.dto.SpendingGrouping;
import com.afinco.backend.api.analytics.dto.SpendingOverviewResponse;
import com.afinco.backend.api.analytics.dto.SpendingPeriodResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.Statement;
import com.afinco.backend.domain.StatementPeriod;
import com.afinco.backend.domain.StatementType;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.mapper.AccountMapper;
import com.afinco.backend.mapper.CategoryMapper;
import com.afinco.backend.mapper.StatementMapper;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.StatementRepository;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.repository.projection.DailyCategorySpending;
import com.afinco.backend.repository.projection.StatementCategorySpending;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class DefaultSpendingAnalyticsServiceTest {

    private static final Clock SEPTEMBER_12_2026 = Clock.fixed(
            LocalDate.of(2026, 9, 12).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private DefaultSpendingAnalyticsService service;
    private Account td;
    private Account rbc;

    @BeforeEach
    void setUp() {
        service = new DefaultSpendingAnalyticsService(
                transactionRepository,
                statementRepository,
                categoryRepository,
                new CategoryMapper(),
                new StatementMapper(new AccountMapper()),
                SEPTEMBER_12_2026);
        td = withId(new Account("TD Bank", "2048", "CAD"), 1L);
        rbc = withId(new Account("RBC", "7310", "CAD"), 2L);
    }

    @Test
    void listsSpendingCategoriesWithoutPayments() {
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(
                withId(new Category("Payment", ExpenseType.PAYMENT, "#10B981"), 1L),
                withId(new Category("Rent", ExpenseType.FIXED, "#4F46E5"), 5L),
                withId(new Category("Groceries", ExpenseType.VARIABLE, "#2563EB"), 6L)));

        SpendingOverviewResponse result = service.findSpending(SpendingGrouping.MONTH, null);

        assertThat(result.categories()).extracting(CategoryResponse::name).containsExactly("Rent", "Groceries");
        assertThat(result.periods()).isEmpty();
        assertThat(result.bankName()).isNull();
    }

    @Test
    void sumsDailySpendingIntoEveryMonthBetweenTheFirstAndLastActivity() {
        when(transactionRepository.sumSpendingByDateAndCategory(null)).thenReturn(List.of(
                day(LocalDate.of(2026, 5, 2), 6L, "10.10", 1),
                day(LocalDate.of(2026, 5, 20), 6L, "5.205", 2),
                day(LocalDate.of(2026, 5, 20), 3L, "40.00", 1),
                day(LocalDate.of(2026, 7, 9), 6L, "12.00", 1)));

        List<SpendingPeriodResponse> periods = service.findSpending(SpendingGrouping.MONTH, " ").periods();

        assertThat(periods).extracting(SpendingPeriodResponse::key).containsExactly("2026-05", "2026-06", "2026-07");
        assertThat(periods.getFirst().startDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(periods.getFirst().endDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(periods.getFirst().statement()).isNull();
        assertThat(periods.getFirst().categories()).containsExactly(
                new CategorySpendingResponse(3L, new BigDecimal("40.00"), 1),
                new CategorySpendingResponse(6L, new BigDecimal("15.31"), 3));
        assertThat(periods.get(1).categories()).as("a month without spending stays on the timeline").isEmpty();
    }

    @Test
    void marksMonthsOutsideContiguousStatementCoverageAsPartial() {
        when(statementRepository.findAllForBankOldestFirst("TD Bank")).thenReturn(List.of(
                statement(10L, td, "2026-02-03", "2026-02-13"),
                statement(11L, td, "2026-02-14", "2026-03-13"),
                statement(12L, td, "2026-03-14", "2026-04-13")));

        List<SpendingPeriodResponse> periods = service.findSpending(SpendingGrouping.MONTH, "TD Bank").periods();

        assertThat(periods).extracting(SpendingPeriodResponse::key).containsExactly("2026-02", "2026-03", "2026-04");
        assertThat(periods).extracting(SpendingPeriodResponse::complete).containsExactly(false, true, false);
    }

    @Test
    void requiresEveryAccountAroundAMonthToCoverAllOfIt() {
        when(statementRepository.findAllForBankOldestFirst(null)).thenReturn(List.of(
                statement(10L, td, "2026-03-01", "2026-05-31"),
                statement(20L, rbc, "2026-04-10", "2026-05-31")));

        List<SpendingPeriodResponse> periods = service.findSpending(SpendingGrouping.MONTH, null).periods();

        assertThat(periods).extracting(SpendingPeriodResponse::complete).containsExactly(true, false, true);
    }

    @Test
    void treatsMonthsWithoutStatementsAsCompleteOnlyOnceTheyAreOver() {
        when(transactionRepository.sumSpendingByDateAndCategory(null)).thenReturn(List.of(
                day(LocalDate.of(2026, 8, 30), 6L, "20.00", 1),
                day(LocalDate.of(2026, 9, 3), 6L, "20.00", 1)));

        List<SpendingPeriodResponse> periods = service.findSpending(SpendingGrouping.MONTH, null).periods();

        assertThat(periods).extracting(SpendingPeriodResponse::complete).containsExactly(true, false);
    }

    @Test
    void groupsOneBanksSpendingByStatementAndFlagsShortBillingPeriods() {
        when(statementRepository.findAllForBankOldestFirst("TD Bank")).thenReturn(List.of(
                statement(10L, td, "2026-02-03", "2026-02-13"),
                statement(11L, td, "2026-02-14", "2026-03-13"),
                statement(12L, td, "2026-03-14", "2026-04-13")));
        when(transactionRepository.sumSpendingByStatementAndCategory("TD Bank")).thenReturn(List.of(
                statementRow(11L, 6L, "88.40", 4),
                statementRow(10L, 6L, "12.00", 1)));

        SpendingOverviewResponse result = service.findSpending(SpendingGrouping.STATEMENT, "TD Bank");

        assertThat(result.bankName()).isEqualTo("TD Bank");
        assertThat(result.periods()).extracting(SpendingPeriodResponse::key).containsExactly("10", "11", "12");
        assertThat(result.periods()).extracting(SpendingPeriodResponse::complete).containsExactly(false, true, true);
        assertThat(result.periods().get(1).statement().statementType()).isEqualTo(StatementType.CREDIT_CARD);
        assertThat(result.periods().get(1).startDate()).isEqualTo(LocalDate.of(2026, 2, 14));
        assertThat(result.periods().get(1).categories())
                .containsExactly(new CategorySpendingResponse(6L, new BigDecimal("88.40"), 4));
        assertThat(result.periods().get(2).categories()).isEmpty();
    }

    @Test
    void refusesToGroupByStatementAcrossBanks() {
        assertThatThrownBy(() -> service.findSpending(SpendingGrouping.STATEMENT, ""))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Choose a bank to group spending by statement");
        verifyNoInteractions(transactionRepository, statementRepository);
    }

    private static Statement statement(long id, Account account, String start, String end) {
        return withId(new Statement(account, StatementType.CREDIT_CARD,
                new StatementPeriod(LocalDate.parse(start), LocalDate.parse(end))), id);
    }

    private static DailyCategorySpending day(LocalDate date, Long categoryId, String amount, long count) {
        return new DailyCategorySpending() {
            @Override
            public LocalDate getDate() {
                return date;
            }

            @Override
            public Long getCategoryId() {
                return categoryId;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal(amount);
            }

            @Override
            public long getTransactionCount() {
                return count;
            }
        };
    }

    private static StatementCategorySpending statementRow(Long statementId, Long categoryId, String amount, long count) {
        return new StatementCategorySpending() {
            @Override
            public Long getStatementId() {
                return statementId;
            }

            @Override
            public Long getCategoryId() {
                return categoryId;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal(amount);
            }

            @Override
            public long getTransactionCount() {
                return count;
            }
        };
    }

    private static <T> T withId(T entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not assign a test identifier", exception);
        }
    }
}
