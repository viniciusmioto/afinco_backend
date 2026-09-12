package com.afinco.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.repository.projection.CategoryAggregation;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class DatabaseIntegrationTest {

    private static final String VALID_HASH = "0123456789abcdef".repeat(4);

    @TempDir
    static Path databaseDirectory;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + databaseDirectory.resolve("afinco-test.db"));
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void connectsToSQLite() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT 1")) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("SQLite");
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void flywayCreatesSchemaAndSeedsCategories() {
        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table'", String.class));
        List<String> categories = jdbcTemplate.queryForList(
                "SELECT name FROM categories ORDER BY id", String.class);

        assertThat(tables).contains("accounts", "categories", "transactions", "flyway_schema_history");
        assertThat(categories).containsExactly(
                "Payment",
                "Subscriptions",
                "Phone / Internet",
                "Transport",
                "Rent",
                "Groceries",
                "Food & Leisure",
                "Pharmacy & Health",
                "Electricity & Water",
                "Occasional");
    }

    @Test
    @Transactional
    void persistsAndReloadsAccountCategoryAndTransaction() {
        Account account = accountRepository.save(new Account("TD Bank", "1234", "CAD"));
        Category category = categoryRepository.findByName("Groceries").orElseThrow();
        Transaction transaction = transactionRepository.save(new Transaction(
                account,
                category,
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.DEBIT,
                "Neighbourhood market",
                VALID_HASH,
                TransactionStatus.CONFIRMED,
                "SEP 11 NEIGHBOURHOOD MARKET 42.35"));
        transactionRepository.flush();
        Long transactionId = transaction.getId();
        entityManager.clear();

        Transaction reloaded = transactionRepository.findById(transactionId).orElseThrow();

        assertThat(reloaded.getAccount().getBankName()).isEqualTo("TD Bank");
        assertThat(reloaded.getCategory().getName()).isEqualTo("Groceries");
        assertThat(reloaded.getAmount()).isEqualByComparingTo("42.35");
        assertThat(reloaded.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    @Transactional
    void filtersTransactionsAndAggregatesConfirmedAmountsByCategory() {
        Account selectedAccount = accountRepository.save(new Account("TD Bank", "1234", "CAD"));
        Account otherAccount = accountRepository.save(new Account("RBC", "5678", "CAD"));
        Category groceries = categoryRepository.findByName("Groceries").orElseThrow();
        Category utilities = categoryRepository.findByName("Occasional").orElseThrow();
        transactionRepository.saveAll(List.of(
                transaction(selectedAccount, groceries, "42.35", "1", TransactionStatus.CONFIRMED),
                transaction(selectedAccount, utilities, "18.20", "2", TransactionStatus.CONFIRMED),
                transaction(selectedAccount, groceries, "42.35", "3", TransactionStatus.DUPLICATE_PENDING),
                transaction(otherAccount, groceries, "99.00", "4", TransactionStatus.CONFIRMED)));
        transactionRepository.flush();

        Page<Transaction> filtered = transactionRepository.findAllMatchingFilters(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                selectedAccount.getId(),
                groceries.getId(),
                TransactionType.DEBIT,
                TransactionStatus.CONFIRMED,
                PageRequest.of(0, 10));
        List<CategoryAggregation> aggregation = transactionRepository.aggregateConfirmedByCategory(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                selectedAccount.getId(),
                TransactionType.DEBIT);
        Page<Transaction> unfiltered = transactionRepository.findAllMatchingFilters(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10));

        assertThat(filtered.getContent())
                .singleElement()
                .extracting(Transaction::getAmount)
                .isEqualTo(new BigDecimal("42.35"));
        assertThat(unfiltered.getTotalElements()).isEqualTo(4);
        assertThat(aggregation).hasSize(2);
        assertThat(aggregation.getFirst().getCategoryName()).isEqualTo("Groceries");
        assertThat(aggregation.getFirst().getTotalAmount()).isEqualByComparingTo("42.35");
        assertThat(aggregation.getFirst().getTransactionCount()).isEqualTo(1);
    }

    private Transaction transaction(
            Account account,
            Category category,
            String amount,
            String hashSeed,
            TransactionStatus status) {
        return new Transaction(
                account,
                category,
                LocalDate.of(2026, 9, 11),
                new BigDecimal(amount),
                TransactionType.DEBIT,
                "Test transaction " + hashSeed,
                hashSeed.repeat(64),
                status,
                "raw line");
    }
}
