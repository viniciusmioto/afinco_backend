package com.afinco.backend.api.transaction.dto;

import java.util.List;

/**
 * Outcome of a reviewed batch save. {@code duplicateCount} counts persisted rows that still
 * matched an existing signature and were not force-imported, so they remain awaiting resolution.
 */
public record TransactionBatchResponse(
        int savedCount,
        int duplicateCount,
        List<TransactionResponse> transactions) {

    public TransactionBatchResponse {
        transactions = List.copyOf(transactions);
    }
}
