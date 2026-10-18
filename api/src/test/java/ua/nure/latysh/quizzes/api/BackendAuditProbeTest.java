package ua.nure.latysh.quizzes.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BackendAuditProbeTest extends ApiContractTestBase {
    @Test
    void aNullAnswerIdPassesValidationAndBecomesAServerError() throws Exception {
        String token = login("student", "secret123", "203.0.113.201");
        long attempt = attemptId(mockMvc.perform(post("/api/v1/quizzes/1/attempts")
                .with(from("203.0.113.201")).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/attempts/" + attempt + "/complete")
                .with(from("203.0.113.201")).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"answerIds\":[null]}"))
                .andExpect(status().isInternalServerError());
        System.out.println("AUDIT_CONFIRMED: null answerIds element returns HTTP 500");
    }

    @Test
    void aNullAdminAnswerPassesValidationAndBecomesAServerError() throws Exception {
        String token = login("admin", "secret123", "203.0.113.202");
        mockMvc.perform(post("/api/v1/admin/quizzes/1/questions")
                .with(from("203.0.113.202")).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"text":"Audit question","answers":[null,
                    {"text":"A","correct":true},
                    {"text":"B","correct":false},
                    {"text":"C","correct":false}]}
                    """))
                .andExpect(status().isInternalServerError());
        System.out.println("AUDIT_CONFIRMED: null admin answers element returns HTTP 500");
    }

    @Test
    void aValidLargeQuizCannotAcceptAllItsCorrectAnswers() throws Exception {
        jdbcTemplate.update("INSERT INTO quizzes (name,time_to_pass,level_id,subject_id)"
                + " VALUES ('Audit large quiz',60,1,1)");
        int quiz = jdbcTemplate.queryForObject(
                "SELECT id FROM quizzes WHERE name='Audit large quiz'", Integer.class);
        for (int i = 0; i < 251; i++) {
            jdbcTemplate.update("INSERT INTO questions (question,quiz_id) VALUES (?,?)",
                    "Audit question " + i, quiz);
        }
        List<Integer> questions = jdbcTemplate.queryForList(
                "SELECT id FROM questions WHERE quiz_id=? ORDER BY id", Integer.class, quiz);
        List<Object[]> answers = new ArrayList<>();
        for (int question : questions) {
            for (int option = 0; option < 4; option++) {
                answers.add(new Object[]{"Option " + option, true, question});
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO answers (answer,correct,question_id) VALUES (?,?,?)", answers);
        String token = login("student", "secret123", "203.0.113.203");
        var started = mockMvc.perform(post("/api/v1/quizzes/" + quiz + "/attempts")
                .with(from("203.0.113.203")).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn();
        long attempt = attemptId(started);
        List<Integer> ids = jdbcTemplate.queryForList(
                "SELECT answer_id FROM attempt_questions WHERE attempt_id=? ORDER BY answer_id",
                Integer.class, attempt);
        assertThat(ids).hasSize(1004);
        mockMvc.perform(post("/api/v1/attempts/" + attempt + "/complete")
                .with(from("203.0.113.203")).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("answerIds", ids))))
                .andExpect(status().isBadRequest());
        var completed = mockMvc.perform(post("/api/v1/attempts/" + attempt + "/complete")
                .with(from("203.0.113.203")).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        java.util.Map.of("answerIds", ids.subList(0, 1000)))))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(completed.getResponse().getContentAsString())
                .get("score").asInt()).isEqualTo(99);
        System.out.println("AUDIT_CONFIRMED: valid 251-question quiz rejects 1004 answers; 1000 scores 99");
    }
}
