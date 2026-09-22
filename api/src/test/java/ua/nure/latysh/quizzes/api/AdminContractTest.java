package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API contract for the administrative surface: who may reach it, and the content it manages. */
class AdminContractTest extends ApiContractTestBase {
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

        verifyAdministrativeAccess(userToken, adminToken);
        int subjectId = manageSubjects(adminToken);
        int quizId = manageQuizzes(adminToken, subjectId);
        manageQuestions(adminToken, quizId);
        manageUsers(adminToken);
        queryAdministrativeResults(adminToken);
        deleteAdministrativeCatalogue(adminToken, quizId, subjectId);
    }

    // Three administrative outcomes the walkthrough above cannot also carry.
    //
    // Not because they do not belong with it, but because the rate limit is
    // real and this class shares one bucket: MockMvc defaults every request to
    // 127.0.0.1, so only the login helper sets an address of its own and every
    // other request in the file counts against the same hundred a minute.
    // These carry their own address for that reason, which is also the only
    // honest way to add requests to this file without moving another test
    // closer to a 429 it has nothing to do with.
    @Test
    void coversTheAdministrativeOutcomesTheWalkthroughHasNoBudgetFor() throws Exception {
        String adminToken = login("admin", "secret123", "192.0.2.72");

        int subjectId = responseId(mockMvc.perform(post("/api/v1/admin/subjects")
                        .with(from("192.0.2.72"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Concurrency\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        int quizId = responseId(mockMvc.perform(post("/api/v1/admin/quizzes")
                        .with(from("192.0.2.72"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Threads","subjectId":%d,"levelId":1,"timeToPassMinutes":15}
                                """.formatted(subjectId)))
                .andExpect(status().isCreated())
                .andReturn());

        String questionRequest = """
                {"text":"%s","answers":[
                  {"text":"One","correct":true},{"text":"Two","correct":false},
                  {"text":"Three","correct":false},{"text":"Four","correct":false}]}
                """;
        try {
            // Two questions on one quiz. Each is written and then read back out
            // of the quiz's whole list, so until there were two, the filter that
            // picks the right one out of that list never had to reject anything.
            int first = responseId(mockMvc.perform(post("/api/v1/admin/quizzes/{quizId}/questions", quizId)
                            .with(from("192.0.2.72"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionRequest.formatted("What is a thread?")))
                    .andExpect(status().isCreated())
                    .andReturn());
            int second = responseId(mockMvc.perform(post("/api/v1/admin/quizzes/{quizId}/questions", quizId)
                            .with(from("192.0.2.72"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionRequest.formatted("What is a lock?")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.text").value("What is a lock?"))
                    .andReturn());
            assertThat(second).as("the second question came back as the first").isNotEqualTo(first);

            mockMvc.perform(put("/api/v1/admin/questions/{questionId}", first)
                            .with(from("192.0.2.72"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(questionRequest.formatted("What is a daemon thread?")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(first))
                    .andExpect(jsonPath("$.text").value("What is a daemon thread?"));

            // An administrator setting their own account to active. The refusal
            // this service has is about locking the last administrator out, not
            // about touching one's own row at all — and only the blocked half of
            // that pair was ever exercised.
            mockMvc.perform(patch("/api/v1/admin/users/5/status")
                            .with(from("192.0.2.72"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"active\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("admin"))
                    .andExpect(jsonPath("$.status").value("active"));

            // Both ends of the range, in the right order: the case the check has
            // to let through. Either end alone, and the pair reversed, were
            // already covered; the ordinary one was not.
            mockMvc.perform(get("/api/v1/admin/results")
                            .param("from", "2026-01-01T00:00:00Z")
                            .param("to", "2026-12-31T23:59:59Z")
                            .with(from("192.0.2.72"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].username")
                            .value(org.hamcrest.Matchers.hasItem("student")));
        } finally {
            jdbcTemplate.update("DELETE FROM answers WHERE question_id IN "
                    + "(SELECT id FROM questions WHERE quiz_id = ?)", quizId);
            jdbcTemplate.update("DELETE FROM questions WHERE quiz_id = ?", quizId);
            jdbcTemplate.update("DELETE FROM quizzes WHERE id = ?", quizId);
            jdbcTemplate.update("DELETE FROM subjects WHERE id = ?", subjectId);
        }
    }

    private void verifyAdministrativeAccess(String userToken, String adminToken) throws Exception {
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
    }

    private int manageSubjects(String adminToken) throws Exception {
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
        return subjectId;
    }

    private int manageQuizzes(String adminToken, int subjectId) throws Exception {
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
        return quizId;
    }

    private void manageQuestions(String adminToken, int quizId) throws Exception {
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
    }

    private void manageUsers(String adminToken) throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
        Tokens emptySession = loginTokens("empty", "secret123", "192.0.2.62");
        mockMvc.perform(patch("/api/v1/admin/users/3/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"blocked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("blocked"));
        mockMvc.perform(get("/api/v1/users/me").with(from("192.0.2.62"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(emptySession.accessToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.62"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(emptySession.refreshToken())))
                .andExpect(status().isUnauthorized());
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
    }

    private void queryAdministrativeResults(String adminToken) throws Exception {
        mockMvc.perform(get("/api/v1/admin/results")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username")
                        .value(org.hamcrest.Matchers.hasItem("student")));
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
    }

    private void deleteAdministrativeCatalogue(String adminToken, int quizId, int subjectId) throws Exception {
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
}
