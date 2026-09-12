package com.afinco.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
