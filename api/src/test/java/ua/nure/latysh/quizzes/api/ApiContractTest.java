package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listsQuizzesAndReturnsOneById() throws Exception {
        mockMvc.perform(get("/api/v1/quizzes"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "100"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Java syntax"))
                .andExpect(jsonPath("$[0].subject").value("Java Basics"))
                .andExpect(jsonPath("$[0].complexity").value("low"))
                .andExpect(jsonPath("$[0].timeToPassMinutes").value(5))
                .andExpect(jsonPath("$[0].totalQuestions").value(2))
                .andExpect(jsonPath("$[1].totalQuestions").value(0));

        mockMvc.perform(get("/api/v1/quizzes/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lists"));
    }

    @Test
    void returnsConsistentErrorsForMissingAndInvalidQuizIdentifiers() throws Exception {
        mockMvc.perform(get("/api/v1/quizzes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Quiz 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/quizzes/99"))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/quizzes/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void exchangesCredentialsForBearerTokenAndReadsCurrentUserResults() throws Exception {
        String studentToken = login("student", "secret123", "192.0.2.10");

        mockMvc.perform(get("/api/v1/results/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));

        mockMvc.perform(get("/api/v1/results/me").with(httpBasic("student", "secret123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptId").value(1))
                .andExpect(jsonPath("$[0].quizId").value(1))
                .andExpect(jsonPath("$[0].quizName").value("Java syntax"))
                .andExpect(jsonPath("$[0].score").value(80))
                .andExpect(jsonPath("$[0].completedAt").value("2026-08-12T10:15:30Z"));

        String emptyToken = login("empty", "secret123", "192.0.2.11");
        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, bearer(emptyToken)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void rejectsInvalidBlockedAndMalformedLoginRequests() throws Exception {
        performLogin("missing", "secret123", "192.0.2.20")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        performLogin("blocked", "secret123", "192.0.2.21")
                .andExpect(status().isUnauthorized());

        performLogin("student", "wrong-password", "192.0.2.22")
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.23");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void enforcesUserAndAdministratorRoles() throws Exception {
        String userToken = login("student", "secret123", "192.0.2.30");
        mockMvc.perform(get("/api/v1/admin/status").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access is denied"));

        String adminToken = login("admin", "secret123", "192.0.2.31");
        mockMvc.perform(get("/api/v1/admin/status").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("quiz-api"))
                .andExpect(jsonPath("$.access").value("admin"));
    }

    @Test
    void appliesCorsAllowlistWithoutRateLimitingPreflightRequests() throws Exception {
        mockMvc.perform(options("/api/v1/results/me")
                        .header(HttpHeaders.ORIGIN, "https://app.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.test"))
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));

        mockMvc.perform(options("/api/v1/results/me")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rateLimitsRepeatedLoginAttemptsByRemoteAddress() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            performLogin("student", "wrong-password", "192.0.2.40")
                    .andExpect(status().isUnauthorized());
        }
        performLogin("student", "secret123", "192.0.2.40")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"));
    }

    @Test
    void healthAndApiDocumentationArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"))
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Quiz REST API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mockMvc.perform(get("/unknown"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password, String remoteAddress) throws Exception {
        MvcResult result = performLogin(username, password, remoteAddress)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private org.springframework.test.web.servlet.ResultActions performLogin(
            String username, String password, String remoteAddress) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password))
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                }));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
