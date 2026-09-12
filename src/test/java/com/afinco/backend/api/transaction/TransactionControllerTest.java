package com.afinco.backend.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.api.transaction.dto.DuplicateResolutionRequest;
import com.afinco.backend.api.transaction.dto.PageResponse;
import com.afinco.backend.api.transaction.dto.TransactionBatchItemRequest;
import com.afinco.backend.api.transaction.dto.TransactionBatchRequest;
import com.afinco.backend.api.transaction.dto.TransactionBatchResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionFilterRequest;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.exception.ConflictException;
import com.afinco.backend.exception.ResourceNotFoundException;
import com.afinco.backend.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void returnsFilteredPaginatedTransactions() throws Exception {
        TransactionResponse transaction = response(TransactionStatus.CONFIRMED);
        when(transactionService.findTransactions(any()))
                .thenReturn(new PageResponse<>(List.of(transaction), 1, 10, 11, 2, false, true));

        mockMvc.perform(get("/api/v1/transactions")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-30")
                        .param("accountId", "1")
                        .param("categoryId", "2")
                        .param("type", "DEBIT")
                        .param("status", "CONFIRMED")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.content[0].description").value("Market"))
                .andExpect(jsonPath("$.content[0].account.bankName").value("TD Bank"));

        ArgumentCaptor<TransactionFilterRequest> captor = ArgumentCaptor.forClass(TransactionFilterRequest.class);
        verify(transactionService).findTransactions(captor.capture());
        assertThat(captor.getValue().accountId()).isEqualTo(1L);
        assertThat(captor.getValue().type()).isEqualTo(TransactionType.DEBIT);
        assertThat(captor.getValue().resolvedSize()).isEqualTo(10);
    }

    @Test
    void createsTransaction() throws Exception {
        TransactionCreateRequest request = createRequest();
        when(transactionService.create(request)).thenReturn(response(TransactionStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/transactions/9"))
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void createsReviewedBatchAndReportsPendingDuplicates() throws Exception {
        TransactionBatchRequest request = new TransactionBatchRequest(4L, List.of(batchItem(true)));
        when(transactionService.createBatch(request)).thenReturn(new TransactionBatchResponse(
                1, 0, List.of(response(TransactionStatus.CONFIRMED))));

        mockMvc.perform(post("/api/v1/transactions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.savedCount").value(1))
                .andExpect(jsonPath("$.duplicateCount").value(0))
                .andExpect(jsonPath("$.transactions[0].status").value("CONFIRMED"));
    }

    @Test
    void rejectsAnEmptyBatch() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": 4, "transactions": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.transactions").exists());

        verify(transactionService, never()).createBatch(any());
    }

    @Test
    void rejectsABatchRowMissingItsCategory() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 4,
                                  "transactions": [
                                    {
                                      "date": "2026-09-11",
                                      "amount": 42.35,
                                      "type": "CREDIT",
                                      "description": "Harbour Market",
                                      "forceDuplicate": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['transactions[0].categoryId']").exists());

        verify(transactionService, never()).createBatch(any());
    }

    @Test
    void rejectsABatchContainingANullRow() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": 4, "transactions": [null]}
                                """))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).createBatch(any());
    }

    @Test
    void resolvesPendingDuplicate() throws Exception {
        DuplicateResolutionRequest request = new DuplicateResolutionRequest(9L, TransactionStatus.CONFIRMED);
        when(transactionService.resolveDuplicate(request)).thenReturn(response(TransactionStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/transactions/resolve-duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void deletesTransaction() throws Exception {
        mockMvc.perform(delete("/api/v1/transactions/9"))
                .andExpect(status().isNoContent());

        verify(transactionService).delete(9L);
    }

    @Test
    void returnsStructuredValidationErrors() throws Exception {
        String invalidRequest = """
                {
                  "accountId": 0,
                  "categoryId": null,
                  "date": null,
                  "amount": -1,
                  "type": null,
                  "description": "",
                  "hashSignature": "invalid"
                }
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/transactions"))
                .andExpect(jsonPath("$.validationErrors.accountId").exists())
                .andExpect(jsonPath("$.validationErrors.categoryId").exists())
                .andExpect(jsonPath("$.validationErrors.hashSignature").exists());
    }

    @Test
    void rejectsInvertedDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("startDate", "2026-09-30")
                        .param("endDate", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.dateRangeValid")
                        .value("startDate must not be after endDate"));
    }

    @Test
    void returnsNotFoundErrorFromService() throws Exception {
        doThrow(new ResourceNotFoundException("Transaction", 99L))
                .when(transactionService)
                .delete(99L);

        mockMvc.perform(delete("/api/v1/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Transaction not found: 99"))
                .andExpect(jsonPath("$.validationErrors").isEmpty());
    }

    @Test
    void returnsConflictWhenTransactionIsNotPending() throws Exception {
        DuplicateResolutionRequest request = new DuplicateResolutionRequest(9L, TransactionStatus.CONFIRMED);
        when(transactionService.resolveDuplicate(request))
                .thenThrow(new ConflictException("Transaction is not awaiting duplicate resolution"));

        mockMvc.perform(post("/api/v1/transactions/resolve-duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Transaction is not awaiting duplicate resolution"));
    }

    @Test
    void rejectsMalformedEnum() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("type", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.type").exists());
    }

    private TransactionBatchItemRequest batchItem(boolean forceDuplicate) {
        return new TransactionBatchItemRequest(
                7L,
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.CREDIT,
                "Harbour Market",
                HASH,
                null,
                forceDuplicate);
    }

    private TransactionCreateRequest createRequest() {
        return new TransactionCreateRequest(
                1L,
                2L,
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.DEBIT,
                "Market",
                HASH,
                "raw line");
    }

    private TransactionResponse response(TransactionStatus status) {
        return new TransactionResponse(
                9L,
                new AccountResponse(1L, "TD Bank", "1234", "CAD"),
                new CategoryResponse(2L, "Groceries", "#2563EB"),
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.DEBIT,
                "Market",
                HASH,
                status,
                "raw line",
                LocalDateTime.of(2026, 9, 11, 12, 0));
    }
}
