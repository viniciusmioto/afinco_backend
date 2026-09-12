package com.afinco.backend.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {

    @TempDir
    static Path databaseDirectory;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:sqlite:" + databaseDirectory.resolve("afinco-auth-test.db"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void logsInSeededUserAndRestoresTheSession() throws Exception {
        LoginContext login = login("test@test.com", "123@Test");

        mockMvc.perform(get("/api/v1/auth/me").session(login.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void reportsSessionStateWithoutRequiringAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").isEmpty());

        LoginContext login = login("test@test.com", "123@Test");
        mockMvc.perform(get("/api/v1/auth/session").session(login.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("test@test.com"));
    }

    @Test
    void returnsStructuredErrorsForUnknownRoutesAndMethods() throws Exception {
        LoginContext login = login("test@test.com", "123@Test");

        mockMvc.perform(get("/api/v1/does-not-exist").session(login.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("The requested API route does not exist"));

        mockMvc.perform(get("/api/v1/transactions/batch").session(login.session()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("The HTTP method is not supported for this route"));
    }

    @Test
    void rejectsInvalidCredentialsWithoutRevealingWhichFieldWasWrong() throws Exception {
        CsrfContext csrf = csrf();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"wrong password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectsFinanceEndpointsAndRejectsMissingCsrfTokens() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));

        LoginContext login = login("test@test.com", "123@Test");
        mockMvc.perform(post("/api/v1/transactions/batch")
                        .session(login.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }

    @Test
    void logoutInvalidatesTheSessionAndExpiresCookies() throws Exception {
        LoginContext login = login("test@test.com", "123@Test");
        CsrfContext csrf = csrf(login.session());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(login.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        mockMvc.perform(get("/api/v1/auth/me").session(login.session()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void persistsAnImportedBatchAcrossLogoutAndLogin() throws Exception {
        LoginContext firstSession = login("test@test.com", "123@Test");
        CsrfContext csrf = csrf(firstSession.session());

        MvcResult accountResult = mockMvc.perform(post("/api/v1/accounts")
                        .session(firstSession.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"TD Bank\",\"accountNumberLast4\":\"1234\",\"currency\":\"cad\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andReturn();
        long accountId = objectMapper.readTree(accountResult.getResponse().getContentAsString()).path("id").asLong();
        long categoryId = objectMapper.readTree(mockMvc.perform(get("/api/v1/categories").session(firstSession.session()))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path(0).path("id").asLong();

        mockMvc.perform(post("/api/v1/transactions/batch")
                        .session(firstSession.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "transactions": [
                                  {"categoryId": %d, "date": "2026-02-12", "amount": 7.00, "type": "CREDIT",
                                   "description": "CHRONO-RECHARGE OPUS MONTREAL", "forceDuplicate": false}
                                ]}
                                """.formatted(accountId, categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.savedCount").value(1));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(firstSession.session())
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent());

        LoginContext secondSession = login("test@test.com", "123@Test");
        mockMvc.perform(get("/api/v1/transactions").session(secondSession.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("CHRONO-RECHARGE OPUS MONTREAL"))
                .andExpect(jsonPath("$.content[0].account.accountNumberLast4").value("1234"));
    }

    private LoginContext login(String email, String password) throws Exception {
        CsrfContext csrf = csrf();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Credentials(email, password))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andReturn();
        return new LoginContext((MockHttpSession) result.getRequest().getSession(false));
    }

    private CsrfContext csrf() throws Exception {
        return csrf(null);
    }

    private CsrfContext csrf(MockHttpSession session) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CsrfContext(
                result.getResponse().getCookie("XSRF-TOKEN"),
                body.path("token").asText());
    }

    private record Credentials(String email, String password) {
    }

    private record CsrfContext(Cookie cookie, String token) {
    }

    private record LoginContext(MockHttpSession session) {
    }
}
