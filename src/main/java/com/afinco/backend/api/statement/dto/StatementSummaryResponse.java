package com.afinco.backend.api.statement.dto;

import com.afinco.backend.domain.StatementType;
import java.time.LocalDate;

/** Compact statement reference embedded in each transaction so clients can show its source. */
public record StatementSummaryResponse(
        Long id,
        StatementType statementType,
        LocalDate periodStart,
        LocalDate periodEnd) {
}
