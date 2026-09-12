package com.afinco.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionMapperTest {

    private final TransactionMapper mapper = new TransactionMapper(new AccountMapper(), new CategoryMapper());

    @Test
    void mapsRequestToEntityAndEntityToResponse() {
        Account account = new Account("TD Bank", "1234", "CAD");
        Category category = new Category("Groceries", "#2563EB");
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
    }
}
