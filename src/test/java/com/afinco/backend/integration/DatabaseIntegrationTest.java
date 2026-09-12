package com.afinco.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.AppUser;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.StatementPeriod;
import com.afinco.backend.domain.StatementType;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.StatementRepository;
import com.afinco.backend.repository.UserRepository;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.repository.projection.CategoryAggregation;
import com.afinco.backend.repository.projection.DailyTransactionCount;
import com.afinco.backend.repository.projection.StatementTransactionCount;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private StatementRepository statementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
        Map<String, String> categories = jdbcTemplate.query(
                "SELECT name, expense_type FROM categories",
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getString("name"), resultSet.getString("expense_type")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(tables).contains("accounts", "categories", "transactions", "users", "flyway_schema_history");
        assertThat(categories).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("Payment", "PAYMENT"),
                Map.entry("Subscriptions", "FIXED"),
                Map.entry("Phone / Internet", "FIXED"),
                Map.entry("Transport", "FIXED"),
                Map.entry("Rent", "FIXED"),
                Map.entry("Groceries", "VARIABLE"),
                Map.entry("Food & Leisure", "VARIABLE"),
                Map.entry("Pharmacy & Health", "VARIABLE"),
                Map.entry("Electricity & Water", "VARIABLE"),
                Map.entry("Occasional", "OCCASIONAL")));
    }

    @Test
    void flywaySeedsBcryptProtectedTestUser() {
        AppUser user = userRepository.findByEmail("test@test.com").orElseThrow();

        assertThat(user.getPasswordHash()).doesNotContain("123@Test");
        assertThat(passwordEncoder.matches("123@Test", user.getPasswordHash())).isTrue();
        assertThat(user.isEnabled()).isTrue();
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
                null,
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

    @Test
    @Transactional
    void storesStatementsAndScopesTransactionsToTheirSourceStatement() {
        Account account = accountRepository.save(new Account("TD Bank", "1234", "CAD"));
        Category category = categoryRepository.findByName("Groceries").orElseThrow();
        StatementPeriod february = new StatementPeriod(LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 13));
        com.afinco.backend.domain.Statement statement = statementRepository.save(
                new com.afinco.backend.domain.Statement(account, StatementType.CREDIT_CARD, february));
        transactionRepository.saveAll(List.of(
                new Transaction(statement, account, category, LocalDate.of(2026, 2, 5), new BigDecimal("7.00"),
                        TransactionType.CREDIT, "Transit", "1".repeat(64), TransactionStatus.CONFIRMED, null),
                new Transaction(statement, account, category, LocalDate.of(2026, 1, 30), new BigDecimal("9.00"),
                        TransactionType.CREDIT, "Late posting", "2".repeat(64), TransactionStatus.CONFIRMED, null),
                new Transaction(account, category, LocalDate.of(2026, 2, 7), new BigDecimal("3.00"),
                        TransactionType.DEBIT, "Manual entry", "3".repeat(64), TransactionStatus.CONFIRMED, null)));
        transactionRepository.flush();
        entityManager.clear();

        Page<Transaction> scoped = transactionRepository.findAllMatchingFilters(
                null, null, statement.getId(), null, null, null, null, PageRequest.of(0, 10));
        Page<Transaction> february2026 = transactionRepository.findAllMatchingFilters(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null, null, null, null, null,
                PageRequest.of(0, 10));

        assertThat(scoped.getContent()).extracting(Transaction::getDescription)
                .containsExactlyInAnyOrder("Transit", "Late posting");
        assertThat(scoped.getContent()).allSatisfy(row ->
                assertThat(row.getStatement().getPeriod()).isEqualTo(february));
        assertThat(february2026.getContent()).extracting(Transaction::getDescription)
                .containsExactlyInAnyOrder("Transit", "Manual entry");
        assertThat(february2026.getContent())
                .filteredOn(row -> row.getDescription().equals("Manual entry"))
                .singleElement()
                .extracting(Transaction::getStatement)
                .isNull();
        assertThat(statementRepository.findByNaturalKey(
                account.getId(), StatementType.CREDIT_CARD, february.startDate(), february.endDate()))
                .get().extracting(com.afinco.backend.domain.Statement::getId).isEqualTo(statement.getId());
        assertThat(transactionRepository.countByStatement())
                .singleElement()
                .extracting(StatementTransactionCount::getTransactionCount)
                .isEqualTo(2L);
        assertThat(transactionRepository.countByDate())
                .extracting(DailyTransactionCount::getDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 2, 5), LocalDate.of(2026, 1, 30), LocalDate.of(2026, 2, 7));
    }

    @Test
    void schemaRejectsASecondStatementForTheSameAccountTypeAndPeriod() {
        Account account = accountRepository.save(new Account("TD Bank", "4321", "CAD"));
        String insert = """
                INSERT INTO statements (account_id, statement_type, period_start, period_end)
                VALUES (?, 'CREDIT_CARD', '2026-03-14', '2026-04-13')
                """;
        jdbcTemplate.update(insert, account.getId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(insert, account.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("UNIQUE");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO statements (account_id, statement_type, period_start, period_end)
                        VALUES (?, 'CREDIT_CARD', '2026-05-13', '2026-04-14')
                        """, account.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("CHECK");
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
