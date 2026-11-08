package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AttemptValidationContractTest extends ApiContractTestBase {
    @Test
    void rejectsNullAnswerIdsWithoutCompletingTheAttempt() throws Exception {
        String token = login("student", "secret123", "203.0.113.201");
        long attempt = attemptId(mockMvc.perform(post("/api/v1/quizzes/1/attempts")
                        .with(from("203.0.113.201")).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(post("/api/v1/attempts/{id}/complete", attempt)
                        .with(from("203.0.113.201")).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answerIds\":[null]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/attempts/{id}/complete", attempt)
                        .with(from("203.0.113.201")).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answerIds\":[1,5,6]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100));
    }

    @Test
    void rejectsMoreAnswersThanTheAttemptSnapshotContains() throws Exception {
        String token = login("student", "secret123", "203.0.113.204");
        long attempt = attemptId(mockMvc.perform(post("/api/v1/quizzes/1/attempts")
                        .with(from("203.0.113.204")).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/attempts/{id}/complete", attempt)
                        .with(from("203.0.113.204")).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerIds\":[1,2,3,4,5,6,7,8,9]}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT completed FROM attempts WHERE id=?", Boolean.class, attempt)).isFalse();
    }

    @Test
    void rejectsNullAdminAnswersWithoutCreatingAQuestion() throws Exception {
        String token = login("admin", "secret123", "203.0.113.202");
        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE quiz_id=1", Integer.class);
        mockMvc.perform(post("/api/v1/admin/quizzes/1/questions")
                        .with(from("203.0.113.202")).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"Question","answers":[null,
                                {"text":"A","correct":true},
                                {"text":"B","correct":false},
                                {"text":"C","correct":false}]}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE quiz_id=1", Integer.class)).isEqualTo(before);
    }

    @Test
    void acceptsEveryCorrectAnswerOfAQuizWithMoreThanOneThousandOptions() throws Exception {
        jdbcTemplate.update("INSERT INTO quizzes (name,time_to_pass,level_id,subject_id)"
                + " VALUES ('Large quiz',60,1,1)");
        int quiz = jdbcTemplate.queryForObject(
                "SELECT id FROM quizzes WHERE name='Large quiz'", Integer.class);
        for (int index = 0; index < 251; index++) {
            jdbcTemplate.update("INSERT INTO questions (question,quiz_id) VALUES (?,?)",
                    "Question " + index, quiz);
        }
        List<Integer> questions = jdbcTemplate.queryForList(
                "SELECT id FROM questions WHERE quiz_id=? ORDER BY id", Integer.class, quiz);
        List<Object[]> answers = new ArrayList<>();
        for (int question : questions) {
            for (int option = 0; option < 4; option++) {
                answers.add(new Object[]{"Option " + option, true, question});
            }
        }
        jdbcTemplate.batchUpdate("INSERT INTO answers (answer,correct,question_id) VALUES (?,?,?)", answers);

        String token = login("student", "secret123", "203.0.113.203");
        var started = mockMvc.perform(post("/api/v1/quizzes/{id}/attempts", quiz)
                        .with(from("203.0.113.203")).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn();
        long attempt = attemptId(started);
        List<Integer> ids = jdbcTemplate.queryForList(
                "SELECT answer_id FROM attempt_questions WHERE attempt_id=? ORDER BY answer_id",
                Integer.class, attempt);
        assertThat(ids).hasSize(1004);
        mockMvc.perform(post("/api/v1/attempts/{id}/complete", attempt)
                        .with(from("203.0.113.203")).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerIds", ids))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(100));
    }
}
