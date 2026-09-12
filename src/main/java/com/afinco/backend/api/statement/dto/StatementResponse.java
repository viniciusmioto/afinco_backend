package com.afinco.backend.api.statement.dto;

import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.domain.StatementType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StatementResponse(
        Long id,
        AccountResponse account,
        StatementType statementType,
        LocalDate periodStart,
        LocalDate periodEnd,
        long transactionCount,
        LocalDateTime importedAt) {
}
