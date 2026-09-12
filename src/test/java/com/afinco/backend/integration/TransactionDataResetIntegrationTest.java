package com.afinco.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionDataResetIntegrationTest {

    @TempDir
    static Path databaseDirectory;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseDirectory.resolve("afinco-reset-test.db"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deletesAllTransactionDataButKeepsAccountsSoStatementsCanBeImportedAgain() throws Exception {
        MockHttpSession session = login();
        long accountId = id(perform(session, post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankName\":\"TD Bank\",\"accountNumberLast4\":\"1234\",\"currency\":\"CAD\"}")));
        long categoryId = objectMapper.readTree(mockMvc.perform(get("/api/v1/categories").session(session))
                .andReturn().getResponse().getContentAsString()).path(0).path("id").asLong();

        mockMvc.perform(authorized(session, post("/api/v1/statements"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statement(accountId, categoryId)))
                .andExpect(status().isCreated());
        mockMvc.perform(authorized(session, post("/api/v1/transactions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "categoryId": %d, "date": "2026-03-01", "amount": 12.00,
                                 "type": "DEBIT", "description": "Manual entry"}
                                """.formatted(accountId, categoryId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/transaction-data").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.statementCount").value(1));

        mockMvc.perform(authorized(session, delete("/api/v1/transaction-data")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedTransactions").value(2))
                .andExpect(jsonPath("$.deletedStatements").value(1));

        mockMvc.perform(get("/api/v1/transaction-data").session(session))
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.statementCount").value(0));
        mockMvc.perform(get("/api/v1/transactions").session(session))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/statements").session(session))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/accounts").session(session))
                .andExpect(jsonPath("$[0].id").value(accountId));

        mockMvc.perform(authorized(session, post("/api/v1/statements"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statement(accountId, categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.duplicateCount").value(0));
    }

    @Test
    void requiresAuthenticationAndACsrfTokenToDelete() throws Exception {
        Csrf anonymousCsrf = csrf(null);
        mockMvc.perform(delete("/api/v1/transaction-data")
                        .cookie(anonymousCsrf.cookie())
                        .header("X-XSRF-TOKEN", anonymousCsrf.token()))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = login();
        mockMvc.perform(delete("/api/v1/transaction-data").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/transaction-data").session(session))
                .andExpect(status().isOk());
    }

    private static String statement(long accountId, long categoryId) {
        return """
                {"accountId": %d, "statementType": "CREDIT_CARD", "periodStart": "2026-02-03", "periodEnd": "2026-02-13",
                 "transactions": [{"categoryId": %d, "date": "2026-02-05", "amount": 7.00,
                                   "description": "Transit", "forceDuplicate": false}]}
                """.formatted(accountId, categoryId);
    }

    private MockHttpSession login() throws Exception {
        Csrf csrf = csrf(null);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"123@Test\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Login rotates the CSRF token, so every unsafe request fetches a fresh one for the session. */
    private MockHttpServletRequestBuilder authorized(MockHttpSession session, MockHttpServletRequestBuilder request)
            throws Exception {
        Csrf csrf = csrf(session);
        return request.session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token());
    }

    private MvcResult perform(MockHttpSession session, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(authorized(session, request)).andExpect(status().isCreated()).andReturn();
    }

    private long id(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"),
                objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText());
    }

    private record Csrf(Cookie cookie, String token) {
    }
}
