package com.afinco.backend.api.statement.dto;

/**
 * Outcome of a statement import. {@code created} is false when the rows were appended to a statement
 * already stored for the same account, type, and period. {@code duplicateCount} counts saved rows that
 * still match an existing signature and await resolution.
 */
public record StatementImportResponse(
        StatementResponse statement,
        boolean created,
        int savedCount,
        int duplicateCount) {
}
