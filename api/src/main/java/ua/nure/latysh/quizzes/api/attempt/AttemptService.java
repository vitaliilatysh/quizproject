package ua.nure.latysh.quizzes.api.attempt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AttemptService {
    private static final String OWNED_ATTEMPT = """
            SELECT attempts.id,
                   attempts.quiz_id,
                   attempts.start_time,
                   attempts.expires_at,
                   attempts.completed,
                   attempts.score,
                   attempts.end_time
            FROM attempts
            JOIN users ON users.id = attempts.user_id
            WHERE attempts.id = :attemptId
              AND users.login = :username
            """;
    private static final String OWNED_ATTEMPT_FOR_UPDATE = OWNED_ATTEMPT + "FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final Clock clock;

    @Autowired
    public AttemptService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    AttemptService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.clock = clock;
    }

    @Transactional
    public AttemptResponse start(int quizId, String username) {
        int durationMinutes = requireReadyQuiz(quizId);
        int userId = requireUserId(username);
        Instant startedAt = clock.instant();
        Instant expiresAt = startedAt.plus(durationMinutes, ChronoUnit.MINUTES);
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO attempts
                        (score, start_time, expires_at, end_time, completed, quiz_id, user_id)
                    VALUES (0, ?, ?, NULL, FALSE, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setTimestamp(1, Timestamp.from(startedAt));
            statement.setTimestamp(2, Timestamp.from(expiresAt));
            statement.setInt(3, quizId);
            statement.setInt(4, userId);
            return statement;
        }, keyHolder);
        long attemptId = keyHolder.getKey().longValue();
        return findOwned(attemptId, username);
    }

    public AttemptResponse findOwned(long attemptId, String username) {
        AttemptRow attempt = findAttempt(attemptId, username, false)
                .orElseThrow(() -> missingAttempt(attemptId));
        return toResponse(attempt);
    }

    @Transactional
    public AttemptCompletionResponse complete(long attemptId, String username, Set<Integer> answerIds) {
        AttemptRow attempt = findAttempt(attemptId, username, true)
                .orElseThrow(() -> missingAttempt(attemptId));
        if (attempt.completed()) {
            throw new ResourceConflictException("Attempt " + attemptId + " was already completed");
        }
        Instant completedAt = clock.instant();
        if (completedAt.isAfter(attempt.expiresAt())) {
            throw new ResourceConflictException("Attempt " + attemptId + " has expired");
        }

        AnswerKey answerKey = loadAnswerKey(attempt.quizId());
        if (answerKey.correctByQuestion().isEmpty()) {
            throw new ResourceConflictException("The attempted quiz no longer contains valid questions");
        }
        Set<Integer> selectedAnswers = Set.copyOf(answerIds);
        if (!answerKey.knownAnswerIds().containsAll(selectedAnswers)) {
            throw new InvalidRequestException("An answer does not belong to the attempted quiz");
        }

        int score = calculateScore(answerKey, selectedAnswers);
        saveAnswers(attemptId, selectedAnswers);
        jdbcTemplate.update("""
                        UPDATE attempts
                        SET score = ?, end_time = ?, completed = TRUE
                        WHERE id = ? AND completed = FALSE
                        """,
                score, Timestamp.from(completedAt), attemptId);
        return new AttemptCompletionResponse(attemptId, attempt.quizId(), score, completedAt);
    }

    private int requireReadyQuiz(int quizId) {
        int duration = jdbcTemplate.query("SELECT time_to_pass FROM quizzes WHERE id = ?",
                        (resultSet, rowNumber) -> resultSet.getInt(1), quizId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Quiz " + quizId + " was not found"));
        int invalidQuestions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT questions.id
                    FROM questions
                    LEFT JOIN answers ON answers.question_id = questions.id
                    WHERE questions.quiz_id = ?
                    GROUP BY questions.id
                    HAVING COUNT(answers.id) <> 4
                        OR SUM(CASE WHEN answers.correct = TRUE THEN 1 ELSE 0 END) = 0
                ) invalid_questions
                """, Integer.class, quizId);
        int totalQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE quiz_id = ?", Integer.class, quizId);
        if (totalQuestions == 0 || invalidQuestions > 0) {
            throw new ResourceConflictException("Quiz " + quizId + " is not ready for attempts");
        }
        return duration;
    }

    private int requireUserId(String username) {
        return jdbcTemplate.query("SELECT id FROM users WHERE login = ?",
                        (resultSet, rowNumber) -> resultSet.getInt(1), username)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Current user was not found"));
    }

    private Optional<AttemptRow> findAttempt(long attemptId, String username, boolean forUpdate) {
        Map<String, Object> parameters = Map.of("attemptId", attemptId, "username", username);
        List<AttemptRow> attempts = forUpdate
                ? namedJdbcTemplate.query(OWNED_ATTEMPT_FOR_UPDATE, parameters, attemptMapper())
                : namedJdbcTemplate.query(OWNED_ATTEMPT, parameters, attemptMapper());
        return attempts
                .stream()
                .findFirst();
    }

    private AttemptResponse toResponse(AttemptRow attempt) {
        return new AttemptResponse(
                attempt.id(),
                attempt.quizId(),
                attempt.startedAt(),
                attempt.expiresAt(),
                attempt.completed(),
                attempt.completed() ? attempt.score() : null,
                attempt.completedAt(),
                loadQuestions(attempt.quizId()));
    }

    private List<AttemptQuestionResponse> loadQuestions(int quizId) {
        var questions = new LinkedHashMap<Integer, MutableQuestion>();
        jdbcTemplate.query("""
                        SELECT questions.id AS question_id,
                               questions.question,
                               answers.id AS answer_id,
                               answers.answer
                        FROM questions
                        JOIN answers ON answers.question_id = questions.id
                        WHERE questions.quiz_id = ?
                        ORDER BY questions.id, answers.id
                        """,
                resultSet -> {
                    int questionId = resultSet.getInt("question_id");
                    String questionText = resultSet.getString("question");
                    var question = questions.computeIfAbsent(questionId,
                            ignored -> new MutableQuestion(questionText));
                    question.answers().add(new AnswerOptionResponse(
                            resultSet.getInt("answer_id"), resultSet.getString("answer")));
                }, quizId);
        return questions.entrySet().stream()
                .map(entry -> new AttemptQuestionResponse(
                        entry.getKey(), entry.getValue().text(), List.copyOf(entry.getValue().answers())))
                .toList();
    }

    private AnswerKey loadAnswerKey(int quizId) {
        var knownAnswerIds = new HashSet<Integer>();
        var correctByQuestion = new LinkedHashMap<Integer, Set<Integer>>();
        var answerToQuestion = new HashMap<Integer, Integer>();
        jdbcTemplate.query("""
                        SELECT questions.id AS question_id,
                               answers.id AS answer_id,
                               answers.correct
                        FROM questions
                        JOIN answers ON answers.question_id = questions.id
                        WHERE questions.quiz_id = ?
                        ORDER BY questions.id, answers.id
                        """,
                resultSet -> {
                    int questionId = resultSet.getInt("question_id");
                    int answerId = resultSet.getInt("answer_id");
                    knownAnswerIds.add(answerId);
                    answerToQuestion.put(answerId, questionId);
                    Set<Integer> correctAnswers = correctByQuestion.computeIfAbsent(
                            questionId, ignored -> new HashSet<>());
                    if (resultSet.getBoolean("correct")) {
                        correctAnswers.add(answerId);
                    }
                }, quizId);
        return new AnswerKey(
                Set.copyOf(knownAnswerIds),
                Map.copyOf(correctByQuestion),
                Map.copyOf(answerToQuestion));
    }

    private static int calculateScore(AnswerKey answerKey, Set<Integer> selectedAnswers) {
        var selectedByQuestion = new HashMap<Integer, Set<Integer>>();
        answerKey.answerToQuestion().forEach((answerId, questionId) -> {
            if (selectedAnswers.contains(answerId)) {
                selectedByQuestion.computeIfAbsent(questionId, ignored -> new HashSet<>()).add(answerId);
            }
        });
        long correctQuestions = answerKey.correctByQuestion().entrySet().stream()
                .filter(entry -> entry.getValue().equals(
                        selectedByQuestion.getOrDefault(entry.getKey(), Set.of())))
                .count();
        return (int) (correctQuestions * 100 / answerKey.correctByQuestion().size());
    }

    private void saveAnswers(long attemptId, Set<Integer> selectedAnswers) {
        if (selectedAnswers.isEmpty()) {
            return;
        }
        List<Object[]> rows = selectedAnswers.stream()
                .map(answerId -> new Object[]{answerId, attemptId})
                .toList();
        jdbcTemplate.batchUpdate(
                "INSERT INTO results (answer_id, attempt_id) VALUES (?, ?)", rows);
    }

    private static RowMapper<AttemptRow> attemptMapper() {
        return (resultSet, rowNumber) -> new AttemptRow(
                resultSet.getLong("id"),
                resultSet.getInt("quiz_id"),
                resultSet.getTimestamp("start_time").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getBoolean("completed"),
                resultSet.getInt("score"),
                nullableInstant(resultSet, "end_time"));
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static ResourceNotFoundException missingAttempt(long attemptId) {
        return new ResourceNotFoundException("Attempt " + attemptId + " was not found");
    }

    private record AttemptRow(
            long id,
            int quizId,
            Instant startedAt,
            Instant expiresAt,
            boolean completed,
            int score,
            Instant completedAt) {
    }

    private record MutableQuestion(String text, List<AnswerOptionResponse> answers) {
        private MutableQuestion(String text) {
            this(text, new ArrayList<>());
        }
    }

    private record AnswerKey(
            Set<Integer> knownAnswerIds,
            Map<Integer, Set<Integer>> correctByQuestion,
            Map<Integer, Integer> answerToQuestion) {
    }
}

