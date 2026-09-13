package com.afinco.backend.api.analytics.dto;

import java.math.BigDecimal;

/** Confirmed spending of one category inside one period. */
public record CategorySpendingResponse(Long categoryId, BigDecimal amount, long transactionCount) {
}
