package com.afinco.backend.api.transaction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Persists a reviewed statement upload. Every row belongs to the same account because a
 * statement covers exactly one account, which keeps the payload free of repeated identifiers.
 */
public record TransactionBatchRequest(
        @NotNull @Positive Long accountId,
        @NotEmpty @Size(max = MAX_BATCH_SIZE) @Valid List<TransactionBatchItemRequest> transactions) {

    public static final int MAX_BATCH_SIZE = 500;

    public TransactionBatchRequest {
        transactions = transactions == null ? null : List.copyOf(transactions);
    }
}
