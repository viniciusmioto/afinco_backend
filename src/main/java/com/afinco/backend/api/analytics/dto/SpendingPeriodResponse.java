package com.afinco.backend.api.analytics.dto;

import com.afinco.backend.api.statement.dto.StatementSummaryResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * One month or one statement of spending.
 *
 * @param key        {@code YYYY-MM} for a month, the statement id for a statement
 * @param statement  the billing statement; null when grouping by month
 * @param complete   false when the period is only partly covered by imported data (a month still in
 *                   progress or outside the imported statements, a short first statement). Averages
 *                   and period-over-period comparisons should skip incomplete periods.
 * @param categories spending per category, ordered by category id; categories without spending are omitted
 */
public record SpendingPeriodResponse(
        String key,
        LocalDate startDate,
        LocalDate endDate,
        StatementSummaryResponse statement,
        boolean complete,
        List<CategorySpendingResponse> categories) {
}
