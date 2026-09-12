package com.afinco.backend.api.transaction.dto;

public record AccountResponse(
        Long id,
        String bankName,
        String accountNumberLast4,
        String currency) {
}
