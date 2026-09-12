package com.afinco.backend.api.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountCreateRequest(
        @NotBlank @Size(max = 100) String bankName,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "must contain exactly four digits") String accountNumberLast4,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter ISO code") String currency) {
}
