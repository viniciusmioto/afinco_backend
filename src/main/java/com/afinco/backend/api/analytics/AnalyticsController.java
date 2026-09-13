package com.afinco.backend.api.analytics;

import com.afinco.backend.api.analytics.dto.SpendingGrouping;
import com.afinco.backend.api.analytics.dto.SpendingOverviewResponse;
import com.afinco.backend.service.SpendingAnalyticsService;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final SpendingAnalyticsService spendingAnalyticsService;

    public AnalyticsController(SpendingAnalyticsService spendingAnalyticsService) {
        this.spendingAnalyticsService = spendingAnalyticsService;
    }

    /** Spending per category per month (every bank or one) or per statement of one bank. */
    @GetMapping("/spending")
    public SpendingOverviewResponse findSpending(
            @RequestParam(defaultValue = "MONTH") SpendingGrouping groupBy,
            @RequestParam(required = false) @Size(max = 100) String bankName) {
        return spendingAnalyticsService.findSpending(groupBy, bankName);
    }
}
