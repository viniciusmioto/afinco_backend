package com.afinco.backend.api.statement.dto;

import com.afinco.backend.domain.StatementType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StatementUploadResponse(
        String bankName,
        StatementType statementType,
        LocalDate periodStart,
        LocalDate periodEnd,
        int transactionCount,
        int duplicateCount,
        BigDecimal total,
        List<ParsedTransactionResponse> transactions) {

    public StatementUploadResponse {
        transactions = List.copyOf(transactions);
    }
}
