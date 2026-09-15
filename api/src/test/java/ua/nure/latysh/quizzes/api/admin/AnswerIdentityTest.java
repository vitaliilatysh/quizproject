package ua.nure.latysh.quizzes.api.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ua.nure.latysh.quizzes.api.admin.AdminModels.AnswerRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionRequest;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An answer's id means the same answer before and after an edit.
 *
 * <p>Editing used to pair the submitted options with the stored rows by
 * position, and the request had no id to pair on instead. The same four options
 * sent in a different order therefore rewrote each other's rows. Nothing was
 * corrupted by that — the text and its correctness travel together, so the
 * question went on asking what the administrator meant — but the id stopped
 * naming what it had named, and ids are what {@code results} rows and an
 * attempt's snapshot are written against.
 */
// Its own database. The class needs a property of its own, which builds a
// second context, and every context re-runs schema.sql — against a database
// that outlives the first one if they share a name.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:answer_identity;MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class AnswerIdentityTest {
    @Autowired
    private QuestionAdminService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Every test here edits the same four rows, and they share one database, so
     * each has to put them back. Ordering the tests instead would only hide
     * which of them depends on which.
     */
    @AfterEach
    void restoreTheSeededAnswers() {
        jdbcTemplate.update("UPDATE answers SET answer = 'Answer 1.1', correct = TRUE WHERE id = 1");
        jdbcTemplate.update("UPDATE answers SET answer = 'Answer 1.2', correct = FALSE WHERE id = 2");
        jdbcTemplate.update("UPDATE answers SET answer = 'Answer 1.3', correct = FALSE WHERE id = 3");
        jdbcTemplate.update("UPDATE answers SET answer = 'Answer 1.4', correct = FALSE WHERE id = 4");
    }

    @Test
    void anAnswerKeepsItsIdentityWhenTheOptionsArriveInAnotherOrder() {
        assertThat(storedAnswers()).containsExactly(
                "1=Answer 1.1 correct", "2=Answer 1.2", "3=Answer 1.3", "4=Answer 1.4");

        // The same four options, unchanged, submitted back to front — and each
        // one naming the row it belongs to.
        service.updateQuestion(1, new QuestionRequest("Question 1", List.of(
                new AnswerRequest(4, "Answer 1.4", false),
                new AnswerRequest(3, "Answer 1.3", false),
                new AnswerRequest(2, "Answer 1.2", false),
                new AnswerRequest(1, "Answer 1.1", true))));

        assertThat(storedAnswers())
                .as("the options were rewritten onto each other's rows")
                .containsExactly("1=Answer 1.1 correct", "2=Answer 1.2", "3=Answer 1.3", "4=Answer 1.4");
    }

    @Test
    void anEditNamingARowFromAnotherQuestionIsRefused() {
        var request = new QuestionRequest("Question 1", List.of(
                new AnswerRequest(5, "Answer 1.1", true),
                new AnswerRequest(2, "Answer 1.2", false),
                new AnswerRequest(3, "Answer 1.3", false),
                new AnswerRequest(4, "Answer 1.4", false)));

        assertThatThrownBy(() -> service.updateQuestion(1, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Answer 5 does not belong to question 1");
    }

    @Test
    void anEditNamingOneRowTwiceIsRefused() {
        var request = new QuestionRequest("Question 1", List.of(
                new AnswerRequest(1, "Answer 1.1", true),
                new AnswerRequest(1, "Answer 1.2", false),
                new AnswerRequest(3, "Answer 1.3", false),
                new AnswerRequest(4, "Answer 1.4", false)));

        assertThatThrownBy(() -> service.updateQuestion(1, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("An answer was named twice");
    }

    /**
     * Half a request is not a request. Naming some rows and not others says
     * nothing about what the unnamed ones are meant to be, and picking either
     * reading — position for those, id for these — would be a guess.
     */
    @Test
    void anEditThatNamesSomeRowsAndNotOthersIsRefused() {
        var request = new QuestionRequest("Question 1", List.of(
                new AnswerRequest(1, "Answer 1.1", true),
                new AnswerRequest("Answer 1.2", false),
                new AnswerRequest(3, "Answer 1.3", false),
                new AnswerRequest(4, "Answer 1.4", false)));

        assertThatThrownBy(() -> service.updateQuestion(1, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Either every answer names the row it edits, or none of them do");
    }

    /**
     * The client that predates the field still works. It sends no ids and gets
     * the old rule, which is correct for it: it echoes the order it was given.
     */
    @Test
    void aRequestWithoutIdsIsStillPairedByPosition() {
        service.updateQuestion(1, new QuestionRequest("Question 1", List.of(
                new AnswerRequest("Answer 1.1 edited", true),
                new AnswerRequest("Answer 1.2", false),
                new AnswerRequest("Answer 1.3", false),
                new AnswerRequest("Answer 1.4", false))));

        assertThat(storedAnswers()).containsExactly(
                "1=Answer 1.1 edited correct", "2=Answer 1.2", "3=Answer 1.3", "4=Answer 1.4");
    }

    private List<String> storedAnswers() {
        return jdbcTemplate.query(
                "SELECT id, answer, correct FROM answers WHERE question_id = 1 ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getInt("id") + "=" + resultSet.getString("answer")
                        + (resultSet.getBoolean("correct") ? " correct" : ""));
    }
}
