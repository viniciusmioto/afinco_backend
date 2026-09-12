package com.afinco.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.time.LocalDate;

/** Inclusive billing period printed on a statement, for example February 3 to February 13, 2026. */
@Embeddable
public record StatementPeriod(
        @Column(name = "period_start", nullable = false) LocalDate startDate,
        @Column(name = "period_end", nullable = false) LocalDate endDate) {

    public StatementPeriod {
        Objects.requireNonNull(startDate, "Statement period start must not be null");
        Objects.requireNonNull(endDate, "Statement period end must not be null");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Statement period start must not be after its end");
        }
    }
}
