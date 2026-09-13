package com.afinco.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.afinco.backend.api.analytics.dto.CategorySpendingResponse;
import com.afinco.backend.api.analytics.dto.SpendingGrouping;
import com.afinco.backend.api.analytics.dto.SpendingOverviewResponse;
import com.afinco.backend.api.analytics.dto.SpendingPeriodResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.Statement;
import com.afinco.backend.domain.StatementPeriod;
import com.afinco.backend.domain.StatementType;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.StatementRepository;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.service.SpendingAnalyticsService;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SpendingAnalyticsIntegrationTest {

    @TempDir
    static Path databaseDirectory;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseDirectory.resolve("analytics.db"));
    }

    @Autowired
    private SpendingAnalyticsService analytics;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StatementRepository statementRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Category groceries;
    private Category transport;
    private int hashSeed;

    @BeforeEach
    void seed() {
        Account td = accountRepository.save(new Account("TD Bank", "2048", "CAD"));
        Account rbc = accountRepository.save(new Account("RBC", "7310", "CAD"));
        groceries = categoryRepository.findByName("Groceries").orElseThrow();
        transport = categoryRepository.findByName("Transport").orElseThrow();
        Category payment = categoryRepository.findByName("Payment").orElseThrow();
        Statement june = statementRepository.save(new Statement(td, StatementType.CREDIT_CARD,
                new StatementPeriod(LocalDate.of(2026, 6, 14), LocalDate.of(2026, 7, 13))));
        Statement july = statementRepository.save(new Statement(td, StatementType.CREDIT_CARD,
                new StatementPeriod(LocalDate.of(2026, 7, 14), LocalDate.of(2026, 8, 13))));

        transactionRepository.saveAll(List.of(
                row(june, td, groceries, "2026-06-20", "30.10", TransactionStatus.CONFIRMED),
                row(june, td, groceries, "2026-07-02", "20.25", TransactionStatus.CONFIRMED),
                row(june, td, payment, "2026-07-05", "500.00", TransactionStatus.CONFIRMED),
                row(july, td, transport, "2026-07-20", "104.50", TransactionStatus.CONFIRMED),
                row(july, td, groceries, "2026-07-21", "30.10", TransactionStatus.DUPLICATE_PENDING),
                row(null, rbc, groceries, "2026-07-22", "9.65", TransactionStatus.CONFIRMED)));
        transactionRepository.flush();
    }

    @Test
    void combinesEveryBanksConfirmedSpendingPerMonthWithoutPayments() {
        SpendingOverviewResponse result = analytics.findSpending(SpendingGrouping.MONTH, null);

        assertThat(result.categories()).extracting(category -> category.name()).doesNotContain("Payment");
        assertThat(result.periods()).extracting(SpendingPeriodResponse::key)
                .containsExactly("2026-06", "2026-07", "2026-08");
        assertThat(groceries.getId()).isLessThan(transport.getId());
        assertThat(result.periods().get(1).categories()).containsExactly(
                new CategorySpendingResponse(groceries.getId(), new BigDecimal("29.90"), 2),
                new CategorySpendingResponse(transport.getId(), new BigDecimal("104.50"), 1));
    }

    @Test
    void limitsStatementsAndTheirSpendingToOneBank() {
        SpendingOverviewResponse result = analytics.findSpending(SpendingGrouping.STATEMENT, "TD Bank");

        assertThat(result.periods()).hasSize(2);
        assertThat(result.periods().getFirst().startDate()).isEqualTo(LocalDate.of(2026, 6, 14));
        assertThat(result.periods().getFirst().categories()).containsExactly(
                new CategorySpendingResponse(groceries.getId(), new BigDecimal("50.35"), 2));
        assertThat(result.periods().getLast().categories()).containsExactly(
                new CategorySpendingResponse(transport.getId(), new BigDecimal("104.50"), 1));
        assertThat(analytics.findSpending(SpendingGrouping.MONTH, "RBC").periods())
                .singleElement()
                .satisfies(month -> assertThat(month.categories()).extracting(CategorySpendingResponse::amount)
                        .containsExactly(new BigDecimal("9.65")));
    }

    private Transaction row(
            Statement statement, Account account, Category category, String date, String amount,
            TransactionStatus status) {
        String hash = String.format("%064x", ++hashSeed);
        return new Transaction(statement, account, category, LocalDate.parse(date), new BigDecimal(amount),
                TransactionType.CREDIT, "Row " + hashSeed, hash, status, null);
    }
}
