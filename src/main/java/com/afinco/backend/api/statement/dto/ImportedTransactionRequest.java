package com.afinco.backend.api.statement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One reviewed statement row. The transaction type comes from the statement type and the duplicate
 * signature is recalculated server-side, so neither is accepted from the client. {@code forceDuplicate}
 * carries the reviewer's explicit "import anyway" decision; skipped rows are never sent.
 */
public record ImportedTransactionRequest(
        @NotNull @Positive Long categoryId,
        @NotNull LocalDate date,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 500) String description,
        boolean forceDuplicate) {
}
