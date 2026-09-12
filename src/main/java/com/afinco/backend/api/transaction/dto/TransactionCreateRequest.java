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
import java.util.Locale;

public record TransactionCreateRequest(
        @NotNull @Positive Long accountId,
        @NotNull @Positive Long categoryId,
        @NotNull LocalDate date,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull TransactionType type,
        @NotBlank @Size(max = 500) String description,
        @Pattern(regexp = "[0-9A-Fa-f]{64}") String hashSignature,
        String rawText) {

    public TransactionCreateRequest {
        hashSignature = hashSignature == null ? null : hashSignature.toLowerCase(Locale.ROOT);
    }
}
