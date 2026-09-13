package com.afinco.backend.api.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.afinco.backend.api.analytics.dto.CategorySpendingResponse;
import com.afinco.backend.api.analytics.dto.SpendingGrouping;
import com.afinco.backend.api.analytics.dto.SpendingOverviewResponse;
import com.afinco.backend.api.analytics.dto.SpendingPeriodResponse;
import com.afinco.backend.api.statement.dto.StatementSummaryResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.StatementType;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.service.SpendingAnalyticsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpendingAnalyticsService spendingAnalyticsService;

    @Test
    void groupsSpendingByMonthAcrossBanksByDefault() throws Exception {
        when(spendingAnalyticsService.findSpending(SpendingGrouping.MONTH, null)).thenReturn(new SpendingOverviewResponse(
                SpendingGrouping.MONTH,
                null,
                List.of(new CategoryResponse(6L, "Groceries", ExpenseType.VARIABLE, "#2563EB")),
                List.of(new SpendingPeriodResponse("2026-07", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                        null, true, List.of(new CategorySpendingResponse(6L, new BigDecimal("412.08"), 9))))));

        mockMvc.perform(get("/api/v1/analytics/spending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("MONTH"))
                .andExpect(jsonPath("$.bankName").doesNotExist())
                .andExpect(jsonPath("$.categories[0].expenseType").value("VARIABLE"))
                .andExpect(jsonPath("$.periods[0].key").value("2026-07"))
                .andExpect(jsonPath("$.periods[0].startDate").value("2026-07-01"))
                .andExpect(jsonPath("$.periods[0].complete").value(true))
                .andExpect(jsonPath("$.periods[0].categories[0].amount").value(412.08))
                .andExpect(jsonPath("$.periods[0].categories[0].transactionCount").value(9));

        verify(spendingAnalyticsService).findSpending(eq(SpendingGrouping.MONTH), isNull());
    }

    @Test
    void groupsOneBanksSpendingByStatement() throws Exception {
        when(spendingAnalyticsService.findSpending(SpendingGrouping.STATEMENT, "TD Bank")).thenReturn(
                new SpendingOverviewResponse(SpendingGrouping.STATEMENT, "TD Bank", List.of(), List.of(
                        new SpendingPeriodResponse("11", LocalDate.of(2026, 7, 14), LocalDate.of(2026, 8, 13),
                                new StatementSummaryResponse(11L, StatementType.CREDIT_CARD,
                                        LocalDate.of(2026, 7, 14), LocalDate.of(2026, 8, 13)),
                                true, List.of()))));

        mockMvc.perform(get("/api/v1/analytics/spending")
                        .param("groupBy", "STATEMENT")
                        .param("bankName", "TD Bank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("TD Bank"))
                .andExpect(jsonPath("$.periods[0].statement.id").value(11))
                .andExpect(jsonPath("$.periods[0].statement.periodEnd").value("2026-08-13"));
    }

    @Test
    void explainsThatStatementsNeedABank() throws Exception {
        when(spendingAnalyticsService.findSpending(SpendingGrouping.STATEMENT, null))
                .thenThrow(new InvalidRequestException("Choose a bank to group spending by statement"));

        mockMvc.perform(get("/api/v1/analytics/spending").param("groupBy", "STATEMENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a bank to group spending by statement"));
    }

    @Test
    void rejectsUnknownGroupingsAndOverlongBankNames() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/spending").param("groupBy", "WEEK"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/analytics/spending").param("bankName", "x".repeat(101)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(spendingAnalyticsService);
    }
}
