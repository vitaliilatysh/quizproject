package ua.nure.latysh.quizzes.api.attempt;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An attempt is scored against the quiz it was issued with, not the quiz as it
 * stands when it is handed in.
 *
 * Scoring used to read the quiz at completion time, so an administrator editing
 * a quiz reached into every attempt already open on it. Both of these failed
 * before the snapshot existed, measured against this same seeded database:
 *
 *   a question added     the reader answered every question they were shown,
 *                        correctly, and scored 66.
 *   a question deleted   their submission carried an answer id that no longer
 *                        belonged to the quiz, so the whole attempt was refused
 *                        with a 400 and could not be finished at all.
 *
 * Both are things an administrator does through the ordinary API, on an
 * ordinary afternoon, without knowing anyone is mid-attempt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttemptSnapshotTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String FOUR_ANSWERS = """
            {"text":"%s","answers":[
              {"text":"One","correct":true},{"text":"Two","correct":false},
              {"text":"Three","correct":false},{"text":"Four","correct":false}]}
            """;

    @Test
    void aQuestionAddedMidAttemptIsNotScoredAgainstTheReader() throws Exception {
        String reader = login("apiuser", "198.51.100.31");
        String administrator = login("admin", "198.51.100.32");

        int attemptId = start(reader, "198.51.100.31");
        int added = createQuestion(administrator, "198.51.100.32", "Added mid attempt");
        try {
            // Answer 1 is the correct one of question 1; 5 and 6 are both correct
            // for question 2. That is every question the attempt was shown, right.
            complete(reader, "198.51.100.31", attemptId, "[1,5,6]")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.score").value(100));
        } finally {
            deleteQuestion(added);
            deleteAttempt(attemptId);
        }
    }

    @Test
    void aQuestionDeletedMidAttemptDoesNotThrowTheAttemptAway() throws Exception {
        String reader = login("empty", "198.51.100.33");
        int attemptId = start(reader, "198.51.100.33");

        // The reader has already been shown question 2 and its four options when
        // the administrator removes it. Their submission still names answer 5.
        jdbcTemplate.update("DELETE FROM results WHERE answer_id IN (5, 6, 7, 8)");
        jdbcTemplate.update("DELETE FROM answers WHERE question_id = 2");
        jdbcTemplate.update("DELETE FROM questions WHERE id = 2");
        try {
            complete(reader, "198.51.100.33", attemptId, "[1,5,6]")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.score").value(100));
        } finally {
            deleteAttempt(attemptId);
            jdbcTemplate.update("INSERT INTO questions VALUES (2, 'Question 2', 1)");
            jdbcTemplate.update("INSERT INTO answers VALUES (5, 'Answer 2.1', TRUE, 2), (6, 'Answer 2.2', TRUE, 2),"
                    + " (7, 'Answer 2.3', FALSE, 2), (8, 'Answer 2.4', FALSE, 2)");
        }
    }

    // Reopening an attempt shows what it was issued with, not what the quiz has
    // become. Without this a reader who refreshed the page mid-attempt would be
    // handed a different set of questions from the one they are scored on.
    @Test
    void reopeningAnAttemptShowsTheQuestionsItWasIssuedWith() throws Exception {
        String reader = login("student", "198.51.100.34");
        String administrator = login("admin", "198.51.100.35");

        int attemptId = start(reader, "198.51.100.34");
        int added = createQuestion(administrator, "198.51.100.35", "Also added mid attempt");
        try {
            mockMvc.perform(get("/api/v1/attempts/{attemptId}", attemptId).with(from("198.51.100.34"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questions.length()").value(2))
                    .andExpect(jsonPath("$.questions[0].answers.length()").value(4));
        } finally {
            deleteQuestion(added);
            deleteAttempt(attemptId);
        }
    }

    // The fallback, and the only window it exists for: a rolling deployment,
    // where an attempt started by a pod that had not migrated yet is completed
    // by one that has. Reading the quiz live is what this service did for every
    // attempt before the snapshot, so such an attempt is no worse off.
    @Test
    void anAttemptWithNoSnapshotIsStillScoredAgainstItsQuiz() throws Exception {
        String reader = login("apiuser", "198.51.100.36");
        int attemptId = start(reader, "198.51.100.36");
        jdbcTemplate.update("DELETE FROM attempt_questions WHERE attempt_id = ?", attemptId);
        try {
            mockMvc.perform(get("/api/v1/attempts/{attemptId}", attemptId).with(from("198.51.100.36"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.questions.length()").value(2));

            complete(reader, "198.51.100.36", attemptId, "[1,5,6]")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.score").value(100));
        } finally {
            deleteAttempt(attemptId);
        }
    }

    @Test
    void startingAnAttemptWritesTheSnapshotItWillBeScoredAgainst() throws Exception {
        String reader = login("student", "198.51.100.37");
        int attemptId = start(reader, "198.51.100.37");
        try {
            Integer options = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM attempt_questions WHERE attempt_id = ?", Integer.class, attemptId);
            Integer correct = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM attempt_questions WHERE attempt_id = ? AND correct = TRUE",
                    Integer.class, attemptId);

            // Quiz 1 is two questions of four options each, three of them correct.
            assertThat(options).isEqualTo(8);
            assertThat(correct).isEqualTo(3);
        } finally {
            deleteAttempt(attemptId);
        }
    }

    private int start(String token, String address) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/quizzes/1/attempts").with(from(address))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andReturn();
        return objectMapper.readTree(started.getResponse().getContentAsString()).get("attemptId").asInt();
    }

    private org.springframework.test.web.servlet.ResultActions complete(
            String token, String address, int attemptId, String answerIds) throws Exception {
        return mockMvc.perform(post("/api/v1/attempts/{attemptId}/complete", attemptId).with(from(address))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerIds\":%s}".formatted(answerIds)));
    }

    private int createQuestion(String token, String address, String text) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/quizzes/1/questions").with(from(address))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FOUR_ANSWERS.formatted(text)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asInt();
    }

    private void deleteQuestion(int questionId) {
        jdbcTemplate.update("DELETE FROM results WHERE answer_id IN"
                + " (SELECT id FROM answers WHERE question_id = ?)", questionId);
        jdbcTemplate.update("DELETE FROM answers WHERE question_id = ?", questionId);
        jdbcTemplate.update("DELETE FROM questions WHERE id = ?", questionId);
    }

    private void deleteAttempt(int attemptId) {
        jdbcTemplate.update("DELETE FROM attempt_questions WHERE attempt_id = ?", attemptId);
        jdbcTemplate.update("DELETE FROM results WHERE attempt_id = ?", attemptId);
        jdbcTemplate.update("DELETE FROM attempts WHERE id = ?", attemptId);
    }

    private String login(String username, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(from(address))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"secret123\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // Its own client address per test: the rate limit is keyed on one, and
    // MockMvc otherwise defaults every request to 127.0.0.1 — a single bucket
    // of a hundred a minute shared by every test in the run.
    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
