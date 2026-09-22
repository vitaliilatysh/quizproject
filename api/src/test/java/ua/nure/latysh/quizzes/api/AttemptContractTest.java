package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API contract for starting, completing and refusing quiz attempts. */
class AttemptContractTest extends ApiContractTestBase {
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

    // A quiz that has questions and still cannot be attempted. "Not ready" has
    // two causes and they are not the same: no questions at all, which quiz 2
    // covers, and questions that are there but incomplete — a question with no
    // answers, or none marked correct — which nothing covered. Starting an
    // attempt on one of those would hand the reader a quiz they cannot score.
    @Test
    void refusesAnAttemptOnAQuizWhoseQuestionsAreIncomplete() throws Exception {
        String token = login("student", "secret123", "192.0.2.71");
        jdbcTemplate.update(
                "INSERT INTO quizzes (id, name, time_to_pass, level_id, subject_id) VALUES (900, 'Half built', 5, 1, 1)");
        jdbcTemplate.update(
                "INSERT INTO questions (id, question, quiz_id) VALUES (900, 'No answers', 900)");
        try {
            mockMvc.perform(post("/api/v1/quizzes/900/attempts")
                            .with(from("192.0.2.71"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Quiz 900 is not ready for attempts"));
        } finally {
            jdbcTemplate.update("DELETE FROM questions WHERE id = 900");
            jdbcTemplate.update("DELETE FROM quizzes WHERE id = 900");
        }
    }

    @Test
    void rejectsAttemptIdsOutsideTheRangeInsteadOfWrappingThemOntoRealOnes() throws Exception {
        String token = login("student", "secret123", "192.0.2.93");

        // 2^32 + 1 narrows to 1 when cast to int, which used to resolve to the
        // caller's attempt 1 — reading, and worse completing, an attempt other
        // than the one addressed.
        mockMvc.perform(get("/api/v1/attempts/4294967297")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/attempts/4294967297/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[1]}"))
                .andExpect(status().isBadRequest());

        // An id that fits but does not exist still reads as missing, not invalid.
        mockMvc.perform(get("/api/v1/attempts/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        // And completing one is the same answer. It is a different query — the
        // completion locks the row it loads — so it needs saying separately.
        mockMvc.perform(post("/api/v1/attempts/999999/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Attempt 999999 was not found"));
    }
}
