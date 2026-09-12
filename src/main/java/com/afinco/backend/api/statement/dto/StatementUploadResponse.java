package com.afinco.backend.api.statement.dto;

import java.math.BigDecimal;
import java.util.List;

public record StatementUploadResponse(
        String bankName,
        int transactionCount,
        int duplicateCount,
        BigDecimal total,
        List<ParsedTransactionResponse> transactions) {

    public StatementUploadResponse {
        transactions = List.copyOf(transactions);
    }
}
