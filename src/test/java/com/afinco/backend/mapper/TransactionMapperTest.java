package com.afinco.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.afinco.backend.api.statement.dto.ImportedTransactionRequest;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.Statement;
import com.afinco.backend.domain.StatementPeriod;
import com.afinco.backend.domain.StatementType;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionMapperTest {

    private final TransactionMapper mapper = new TransactionMapper(
            new AccountMapper(), new CategoryMapper(), new StatementMapper(new AccountMapper()));

    @Test
    void mapsRequestToEntityAndEntityToResponse() {
        Account account = new Account("TD Bank", "1234", "CAD");
        Category category = new Category("Groceries", ExpenseType.VARIABLE, "#2563EB");
        TransactionCreateRequest request = new TransactionCreateRequest(
                1L,
                2L,
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.DEBIT,
                "Market",
                "A".repeat(64),
                "raw line");

        Transaction entity = mapper.toEntity(request, account, category, TransactionStatus.CONFIRMED, "a".repeat(64));
        TransactionResponse response = mapper.toResponse(entity);

        assertThat(response.account().bankName()).isEqualTo("TD Bank");
        assertThat(response.category().name()).isEqualTo("Groceries");
        assertThat(response.amount()).isEqualByComparingTo("42.35");
        assertThat(response.hashSignature()).isEqualTo("a".repeat(64));
        assertThat(response.status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(response.rawText()).isEqualTo("raw line");
        assertThat(response.statement()).isNull();
    }

    @Test
    void mapsAnImportedRowWithItsStatementAccountTypeAndSummary() {
        Account account = new Account("TD Bank", "1234", "CAD");
        Category category = new Category("Groceries", ExpenseType.VARIABLE, "#2563EB");
        Statement statement = new Statement(account, StatementType.CREDIT_CARD,
                new StatementPeriod(LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 13)));
        ImportedTransactionRequest request = new ImportedTransactionRequest(
                2L, LocalDate.of(2026, 2, 5), new BigDecimal("7.00"), "Transit", false);

        Transaction entity = mapper.toEntity(request, statement, category, TransactionStatus.CONFIRMED, "b".repeat(64));
        TransactionResponse response = mapper.toResponse(entity);

        assertThat(entity.getAccount()).isSameAs(account);
        assertThat(entity.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(entity.getRawText()).isNull();
        assertThat(response.statement().statementType()).isEqualTo(StatementType.CREDIT_CARD);
        assertThat(response.statement().periodStart()).isEqualTo(LocalDate.of(2026, 2, 3));
        assertThat(response.statement().periodEnd()).isEqualTo(LocalDate.of(2026, 2, 13));
    }
}
