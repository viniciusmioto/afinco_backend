package com.afinco.backend.service;

import com.afinco.backend.api.analytics.dto.SpendingGrouping;
import com.afinco.backend.api.analytics.dto.SpendingOverviewResponse;

public interface SpendingAnalyticsService {

    /**
     * Confirmed spending per category per period. Months may combine every bank (a null or blank bank
     * name); statements always belong to one bank, so grouping by statement requires one.
     */
    SpendingOverviewResponse findSpending(SpendingGrouping groupBy, String bankName);
}
