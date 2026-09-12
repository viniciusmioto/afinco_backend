package com.afinco.backend.api.transaction.dto;

import com.afinco.backend.domain.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DuplicateResolutionRequest(
        @NotNull @Positive Long transactionId,
        @NotNull TransactionStatus status) {
}
