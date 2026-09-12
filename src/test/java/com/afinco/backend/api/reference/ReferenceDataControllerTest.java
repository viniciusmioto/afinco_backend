package com.afinco.backend.api.reference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.afinco.backend.api.transaction.dto.AccountCreateRequest;
import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.service.ReferenceDataService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AccountController.class, CategoryController.class})
@AutoConfigureMockMvc(addFilters = false)
class ReferenceDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReferenceDataService referenceDataService;

    @Test
    void listsAccounts() throws Exception {
        when(referenceDataService.findAccounts())
                .thenReturn(List.of(new AccountResponse(4L, "TD Bank", "2048", "CAD")));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].bankName").value("TD Bank"))
                .andExpect(jsonPath("$[0].accountNumberLast4").value("2048"));
    }

    @Test
    void listsCategories() throws Exception {
        when(referenceDataService.findCategories())
                .thenReturn(List.of(new CategoryResponse(2L, "Groceries", ExpenseType.VARIABLE, "#2563EB")));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].name").value("Groceries"))
                .andExpect(jsonPath("$[0].colorCode").value("#2563EB"));
    }

    @Test
    void returnsEmptyListWhenNoAccountsExist() throws Exception {
        when(referenceDataService.findAccounts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createsAccount() throws Exception {
        AccountCreateRequest request = new AccountCreateRequest("TD Bank", "1234", "CAD");
        when(referenceDataService.createAccount(request))
                .thenReturn(new AccountResponse(7L, "TD Bank", "1234", "CAD"));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"TD Bank\",\"accountNumberLast4\":\"1234\",\"currency\":\"CAD\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/accounts/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.accountNumberLast4").value("1234"));
    }

    @Test
    void rejectsInvalidAccountWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\" \",\"accountNumberLast4\":\"12a4\",\"currency\":\"CADX\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.bankName").exists())
                .andExpect(jsonPath("$.validationErrors.accountNumberLast4").value("must contain exactly four digits"))
                .andExpect(jsonPath("$.validationErrors.currency").value("must be a three-letter ISO code"));

        verify(referenceDataService, never()).createAccount(any());
    }
}
