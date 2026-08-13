package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiContractTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsQuizzesAndReturnsOneById() throws Exception {
        mockMvc.perform(get("/api/v1/quizzes"))
                .andExpect(status().isOk())
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
    void currentUserResultsRequireValidActiveCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/results/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/results/me").with(httpBasic("missing", "secret123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/results/me").with(httpBasic("blocked", "secret123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/results/me").with(httpBasic("student", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptId").value(1))
                .andExpect(jsonPath("$[0].quizId").value(1))
                .andExpect(jsonPath("$[0].quizName").value("Java syntax"))
                .andExpect(jsonPath("$[0].score").value(80))
                .andExpect(jsonPath("$[0].completedAt").value("2026-08-12T10:15:30Z"));

        mockMvc.perform(get("/api/v1/results/me").with(httpBasic("empty", "secret123")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void healthAndApiDocumentationArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Quiz REST API"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"));

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mockMvc.perform(get("/unknown"))
                .andExpect(status().isUnauthorized());
    }
}
