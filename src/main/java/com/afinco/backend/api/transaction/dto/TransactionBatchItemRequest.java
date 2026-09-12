package com.afinco.backend.api.transaction.dto;

import com.afinco.backend.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One reviewed statement row awaiting persistence. {@code forceDuplicate} carries the
 * reviewer's explicit "import anyway" decision for a row the upload preview flagged;
 * rows the reviewer skipped are never sent.
 */
public record TransactionBatchItemRequest(
        @NotNull @Positive Long categoryId,
        @NotNull LocalDate date,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type,
        @NotBlank @Size(max = 500) String description,
        @Pattern(regexp = "[0-9A-Fa-f]{64}") String hashSignature,
        String rawText,
        boolean forceDuplicate) {

    /**
     * Reuses the single-create payload so batch and single writes share one persistence contract.
     * The signature is recalculated server-side, so the previewed {@code hashSignature} is not propagated.
     */
    public TransactionCreateRequest toCreateRequest(Long accountId) {
        return new TransactionCreateRequest(
                accountId, categoryId, date, amount, type, description, null, rawText);
    }
}
