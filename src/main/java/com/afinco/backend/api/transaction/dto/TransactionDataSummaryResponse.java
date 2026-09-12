package com.afinco.backend.api.transaction.dto;

/** What a full transaction-data reset would remove. */
public record TransactionDataSummaryResponse(long transactionCount, long statementCount) {
}
