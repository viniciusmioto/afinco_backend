package com.afinco.backend.api.analytics.dto;

/** How spending periods are cut: calendar months across banks, or one bank's statement billing periods. */
public enum SpendingGrouping {
    MONTH,
    STATEMENT
}
