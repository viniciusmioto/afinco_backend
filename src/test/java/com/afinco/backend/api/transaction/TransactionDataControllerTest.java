package com.afinco.backend.api.transaction;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.afinco.backend.api.transaction.dto.TransactionDataDeletionResponse;
import com.afinco.backend.api.transaction.dto.TransactionDataSummaryResponse;
import com.afinco.backend.service.TransactionDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionDataController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionDataService transactionDataService;

    @Test
    void summarizesWhatAResetWouldDelete() throws Exception {
        when(transactionDataService.summarize()).thenReturn(new TransactionDataSummaryResponse(250, 7));

        mockMvc.perform(get("/api/v1/transaction-data"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.transactionCount").value(250))
                .andExpect(jsonPath("$.statementCount").value(7));
    }

    @Test
    void deletesAllTransactionDataAndReportsCounts() throws Exception {
        when(transactionDataService.deleteAll()).thenReturn(new TransactionDataDeletionResponse(250, 7));

        mockMvc.perform(delete("/api/v1/transaction-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedTransactions").value(250))
                .andExpect(jsonPath("$.deletedStatements").value(7));

        verify(transactionDataService).deleteAll();
    }
}
