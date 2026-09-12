package com.afinco.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DomainModelTest {

    private static final String VALID_HASH = "a".repeat(64);

    @Test
    void normalizesAccountCurrencyAndCategoryColor() {
        Account account = new Account("TD Bank", "1234", "cad");
        Category category = new Category("Groceries", ExpenseType.VARIABLE, "#a1b2c3");

        assertThat(account.getCurrency()).isEqualTo("CAD");
        assertThat(category.getColorCode()).isEqualTo("#A1B2C3");
    }

    @Test
    void normalizesUserEmailAndRequiresBcryptHash() {
        AppUser user = new AppUser(
                "  Test@Example.COM ",
                "$2a$12$9fgMDPBPa4uXj7rW8WNGIuO.vvEIR2FmRX8yNt1xIzFD535.4MfRa");

        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AppUser("test@example.com", "plain text"))
                .withMessageContaining("BCrypt");
    }

    @Test
    void rejectsAStatementPeriodThatEndsBeforeItStarts() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StatementPeriod(LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 3)))
                .withMessageContaining("start");
        assertThatNullPointerException()
                .isThrownBy(() -> new StatementPeriod(null, LocalDate.of(2026, 2, 3)));
        assertThat(new StatementPeriod(LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 3)).endDate())
                .isEqualTo(LocalDate.of(2026, 2, 3));
    }

    @Test
    void derivesTheTransactionTypeFromTheStatementType() {
        assertThat(StatementType.CREDIT_CARD.transactionType()).isEqualTo(TransactionType.CREDIT);
        assertThat(StatementType.CHECKING_ACCOUNT.transactionType()).isEqualTo(TransactionType.DEBIT);
    }

    @Test
    void keepsAStatementTransactionOnTheStatementsAccount() throws ReflectiveOperationException {
        Account statementAccount = withId(new Account("TD Bank", "1234", "CAD"), 1L);
        Account otherAccount = withId(new Account("RBC", "5678", "CAD"), 2L);
        Category category = new Category("Groceries", ExpenseType.VARIABLE, "#2563EB");
        Statement statement = new Statement(statementAccount, StatementType.CREDIT_CARD,
                new StatementPeriod(LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 13)));

        Transaction linked = new Transaction(statement, statementAccount, category, LocalDate.of(2026, 2, 5),
                BigDecimal.ONE, TransactionType.CREDIT, "Transit", VALID_HASH, TransactionStatus.CONFIRMED, null);

        assertThat(linked.getStatement()).isSameAs(statement);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Transaction(statement, otherAccount, category, LocalDate.of(2026, 2, 5),
                        BigDecimal.ONE, TransactionType.CREDIT, "Transit", VALID_HASH,
                        TransactionStatus.CONFIRMED, null))
                .withMessageContaining("statement's account");
    }

    private static Account withId(Account account, long id) throws ReflectiveOperationException {
        var field = Account.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(account, id);
        return account;
    }

    @Test
    void rejectsInvalidAccountNumberSuffix() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Account("TD Bank", "12A4", "CAD"))
                .withMessageContaining("four digits");
    }

    @Test
    void rejectsNegativeTransactionAmount() {
        Account account = new Account("TD Bank", "1234", "CAD");
        Category category = new Category("Groceries", ExpenseType.VARIABLE, "#2563EB");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Transaction(
                        account,
                        category,
                        LocalDate.of(2026, 9, 11),
                        new BigDecimal("-1.00"),
                        TransactionType.DEBIT,
                        "Market",
                        VALID_HASH,
                        TransactionStatus.CONFIRMED,
                        "raw line"))
                .withMessageContaining("greater than zero");
    }

    @Test
    void rejectsMalformedTransactionSignature() {
        Account account = new Account("RBC", "5678", "CAD");
        Category category = new Category("Income", ExpenseType.VARIABLE, "#16A34A");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Transaction(
                        account,
                        category,
                        LocalDate.of(2026, 9, 11),
                        new BigDecimal("100.00"),
                        TransactionType.CREDIT,
                        "Payroll",
                        "not-a-hash",
                        TransactionStatus.CONFIRMED,
                        null))
                .withMessageContaining("SHA-256");
    }

    @Test
    void confirmsPendingDuplicate() {
        Transaction transaction = new Transaction(
                new Account("RBC", "5678", "CAD"),
                new Category("Income", ExpenseType.VARIABLE, "#16A34A"),
                LocalDate.of(2026, 9, 11),
                new BigDecimal("100.00"),
                TransactionType.CREDIT,
                "Payroll",
                VALID_HASH,
                TransactionStatus.DUPLICATE_PENDING,
                null);

        transaction.confirmDuplicate();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
    }
}
