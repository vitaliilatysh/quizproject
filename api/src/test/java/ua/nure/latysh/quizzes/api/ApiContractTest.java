package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void providesCompleteAdministrativeContentManagement() throws Exception {
        String userToken = login("student", "secret123", "192.0.2.60");
        String adminToken = login("admin", "secret123", "192.0.2.61");

        mockMvc.perform(get("/api/v1/admin/subjects"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Collections"));
        mockMvc.perform(get("/api/v1/admin/levels")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("low"));
        mockMvc.perform(get("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subjectId").value(1));

        int subjectId = responseId(mockMvc.perform(post("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Data Science \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Data Science"))
                .andReturn());

        mockMvc.perform(post("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java Basics\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/subjects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/admin/subjects/{subjectId}", subjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Data Engineering\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Data Engineering"));
        mockMvc.perform(put("/api/v1/admin/subjects/{subjectId}", subjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java Basics\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/admin/subjects/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/subjects/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Subject 1 is used by a quiz"));

        String quizRequest = """
                {"name":"Data structures","subjectId":%d,"levelId":1,"timeToPassMinutes":15}
                """.formatted(subjectId);
        int quizId = responseId(mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quizRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("Data Engineering"))
                .andReturn());

        mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java syntax\",\"subjectId\":1,\"levelId\":1,\"timeToPassMinutes\":5}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing subject\",\"subjectId\":999,\"levelId\":1,\"timeToPassMinutes\":5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing level\",\"subjectId\":1,\"levelId\":999,\"timeToPassMinutes\":5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad duration\",\"subjectId\":1,\"levelId\":1,\"timeToPassMinutes\":0}"))
                .andExpect(status().isBadRequest());

        String updatedQuiz = """
                {"name":"Algorithms","subjectId":%d,"levelId":2,"timeToPassMinutes":20}
                """.formatted(subjectId);
        mockMvc.perform(put("/api/v1/admin/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedQuiz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Algorithms"))
                .andExpect(jsonPath("$.complexity").value("medium"));
        mockMvc.perform(put("/api/v1/admin/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lists\",\"subjectId\":2,\"levelId\":2,\"timeToPassMinutes\":10}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/admin/quizzes/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedQuiz))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/admin/quizzes/{quizId}/questions", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/api/v1/admin/quizzes/999/questions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());

        String questionRequest = """
                {"text":"What is a stack?","answers":[
                  {"text":"LIFO","correct":true},{"text":"FIFO","correct":false},
                  {"text":"Tree","correct":false},{"text":"Graph","correct":false}]}
                """;
        int questionId = responseId(mockMvc.perform(post(
                                "/api/v1/admin/quizzes/{quizId}/questions", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answers.length()").value(4))
                .andReturn());

        String updatedQuestion = questionRequest.replace("What is a stack?", "Choose LIFO")
                .replace("\"LIFO\"", "\"Stack\"");
        mockMvc.perform(put("/api/v1/admin/questions/{questionId}", questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedQuestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Choose LIFO"))
                .andExpect(jsonPath("$.answers[0].text").value("Stack"));
        mockMvc.perform(post("/api/v1/admin/quizzes/{quizId}/questions", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest.replace("true", "false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A question must have at least one correct answer"));
        mockMvc.perform(post("/api/v1/admin/quizzes/{quizId}/questions", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Too few\",\"answers\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/quizzes/999/questions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest))
                .andExpect(status().isNotFound());

        jdbcTemplate.update("INSERT INTO questions (id, question, quiz_id) VALUES (99, 'Broken', ?)", quizId);
        mockMvc.perform(put("/api/v1/admin/questions/99")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Question 99 does not contain exactly four answers"));
        mockMvc.perform(delete("/api/v1/admin/questions/99")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/admin/questions/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(questionRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/questions/{questionId}", questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/questions/{questionId}", questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"blocked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("blocked"));
        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
        mockMvc.perform(patch("/api/v1/admin/users/5/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"blocked\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/admin/users/999/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"paused\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/results")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("student"));
        mockMvc.perform(get("/api/v1/admin/results")
                        .param("from", "2026-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/results")
                        .param("to", "2026-12-31T23:59:59Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/results")
                        .param("from", "2027-01-01T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/admin/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/quizzes/{quizId}", quizId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/subjects/{subjectId}", subjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/subjects/{subjectId}", subjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void completesAnOwnedQuizAttemptWithoutLeakingCorrectAnswers() throws Exception {
        String token = login("apiuser", "secret123", "192.0.2.50");
        MvcResult started = startAttempt(token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quizId").value(1))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].text").value("Question 1"))
                .andExpect(jsonPath("$.questions[0].answers.length()").value(4))
                .andExpect(jsonPath("$.questions[0].answers[0].text").value("Answer 1.1"))
                .andExpect(jsonPath("$.questions[0].answers[0].correct").doesNotExist())
                .andReturn();
        long attemptId = objectMapper.readTree(started.getResponse().getContentAsString()).get("attemptId").asLong();

        mockMvc.perform(get("/api/v1/attempts/{attemptId}", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId));

        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[1,5,6]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId))
                .andExpect(jsonPath("$.quizId").value(1))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(get("/api/v1/attempts/{attemptId}", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", attemptId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Attempt " + attemptId + " was already completed"));
    }

    @Test
    void rejectsInvalidUnauthorizedAndStaleAttemptOperations() throws Exception {
        mockMvc.perform(post("/api/v1/quizzes/1/attempts"))
                .andExpect(status().isUnauthorized());

        String token = login("apiuser", "secret123", "192.0.2.51");
        mockMvc.perform(post("/api/v1/quizzes/0/attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/quizzes/99/attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Quiz 99 was not found"));
        mockMvc.perform(post("/api/v1/quizzes/2/attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Quiz 2 is not ready for attempts"));
        mockMvc.perform(get("/api/v1/attempts/999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/attempts/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/attempts/2/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Attempt 2 has expired"));
        mockMvc.perform(post("/api/v1/attempts/3/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The attempted quiz no longer contains valid questions"));

        long invalidAnswerAttempt = attemptId(startAttempt(token).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", invalidAnswerAttempt)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[999]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An answer does not belong to the attempted quiz"));

        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", invalidAnswerAttempt)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", invalidAnswerAttempt)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[0]}"))
                .andExpect(status().isBadRequest());

        long unansweredAttempt = attemptId(startAttempt(token).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", unansweredAttempt)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void rejectsATokenWhoseUserWasRemovedAfterLogin() throws Exception {
        String token = login("orphan", "secret123", "192.0.2.52");
        jdbcTemplate.update("DELETE FROM users WHERE id = 7");
        try {
            mockMvc.perform(post("/api/v1/quizzes/1/attempts")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Current user was not found"));
        } finally {
            jdbcTemplate.update("INSERT INTO users VALUES (7, 'orphan', 'secret123', 1, 2)");
        }
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

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
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

    private org.springframework.test.web.servlet.ResultActions startAttempt(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/quizzes/1/attempts")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private long attemptId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("attemptId").asLong();
    }

    private int responseId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
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

