package com.afinco.backend.api.analytics.dto;

import com.afinco.backend.api.transaction.dto.CategoryResponse;
import java.util.List;

/**
 * Spending per category per period, oldest period first. Payments are transfers rather than
 * spending, so payment categories and their transactions are excluded.
 *
 * @param bankName   the bank the figures are limited to; null for every bank
 * @param categories every spending category, so clients can label and type each period's rows
 */
public record SpendingOverviewResponse(
        SpendingGrouping groupBy,
        String bankName,
        List<CategoryResponse> categories,
        List<SpendingPeriodResponse> periods) {
}
