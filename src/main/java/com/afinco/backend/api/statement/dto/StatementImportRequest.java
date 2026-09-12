package com.afinco.backend.api.statement.dto;

import com.afinco.backend.domain.StatementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** A reviewed statement preview to persist: the statement itself plus the rows the reviewer kept. */
public record StatementImportRequest(
        @NotNull @Positive Long accountId,
        @NotNull StatementType statementType,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotEmpty @Size(max = MAX_TRANSACTIONS) @Valid List<@NotNull ImportedTransactionRequest> transactions) {

    public static final int MAX_TRANSACTIONS = 500;

    public StatementImportRequest {
        transactions = transactions == null ? null : List.copyOf(transactions);
    }

    @AssertTrue(message = "periodStart must not be after periodEnd")
    public boolean isPeriodValid() {
        return periodStart == null || periodEnd == null || !periodStart.isAfter(periodEnd);
    }
}
