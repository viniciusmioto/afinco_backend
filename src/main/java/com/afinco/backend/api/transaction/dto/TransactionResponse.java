package com.afinco.backend.api.transaction.dto;

import com.afinco.backend.api.statement.dto.StatementSummaryResponse;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        AccountResponse account,
        CategoryResponse category,
        StatementSummaryResponse statement,
        LocalDate date,
        BigDecimal amount,
        TransactionType type,
        String description,
        String hashSignature,
        TransactionStatus status,
        String rawText,
        LocalDateTime createdAt) {
}
