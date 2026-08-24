package ua.nure.latysh.quizzes.api.admin;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.admin.AdminModels.AnswerRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.AnswerResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.LevelResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuizRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuizResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.ResultResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.SubjectResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.UserResponse;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class AdminService {
    private final JdbcTemplate jdbcTemplate;

    public AdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SubjectResponse> subjects() {
        return jdbcTemplate.query("SELECT id, name FROM subjects ORDER BY name",
                (resultSet, rowNumber) -> new SubjectResponse(
                        resultSet.getInt("id"), resultSet.getString("name")));
    }

    public List<LevelResponse> levels() {
        return jdbcTemplate.query("SELECT id, level FROM levels ORDER BY id",
                (resultSet, rowNumber) -> new LevelResponse(
                        resultSet.getInt("id"), resultSet.getString("level")));
    }

    public List<QuizResponse> quizzes() {
        return jdbcTemplate.query("""
                        SELECT quizzes.id, quizzes.name, quizzes.time_to_pass,
                               quizzes.level_id, levels.level,
                               quizzes.subject_id, subjects.name AS subject_name,
                               COUNT(questions.id) AS total_questions
                        FROM quizzes
                        JOIN levels ON levels.id = quizzes.level_id
                        JOIN subjects ON subjects.id = quizzes.subject_id
                        LEFT JOIN questions ON questions.quiz_id = quizzes.id
                        GROUP BY quizzes.id, quizzes.name, quizzes.time_to_pass,
                                 quizzes.level_id, levels.level, quizzes.subject_id, subjects.name
                        ORDER BY quizzes.id
                        """,
                (resultSet, rowNumber) -> new QuizResponse(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getInt("time_to_pass"),
                        resultSet.getInt("level_id"),
                        resultSet.getString("level"),
                        resultSet.getInt("subject_id"),
                        resultSet.getString("subject_name"),
                        resultSet.getInt("total_questions")));
    }

    public List<QuestionResponse> questions(int quizId) {
        requireExists("quizzes", quizId, "Quiz");
        var questions = new LinkedHashMap<Integer, MutableQuestion>();
        jdbcTemplate.query("""
                        SELECT questions.id AS question_id, questions.question,
                               answers.id AS answer_id, answers.answer, answers.correct
                        FROM questions
                        LEFT JOIN answers ON answers.question_id = questions.id
                        WHERE questions.quiz_id = ?
                        ORDER BY questions.id, answers.id
                        """,
                resultSet -> {
                    int questionId = resultSet.getInt("question_id");
                    String questionText = resultSet.getString("question");
                    var question = questions.computeIfAbsent(questionId,
                            ignored -> new MutableQuestion(questionText));
                    int answerId = resultSet.getInt("answer_id");
                    if (!resultSet.wasNull()) {
                        question.answers().add(new AnswerResponse(
                                answerId,
                                resultSet.getString("answer"),
                                resultSet.getBoolean("correct")));
                    }
                }, quizId);
        return questions.entrySet().stream()
                .map(entry -> new QuestionResponse(
                        entry.getKey(), quizId, entry.getValue().text(), List.copyOf(entry.getValue().answers())))
                .toList();
    }

    public SubjectResponse createSubject(String name) {
        String normalizedName = name.trim();
        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(
                        "INSERT INTO subjects (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, normalizedName);
                return statement;
            }, keyHolder);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate("Subject", normalizedName);
        }
        return new SubjectResponse(keyHolder.getKey().intValue(), normalizedName);
    }

    public SubjectResponse updateSubject(int subjectId, String name) {
        String normalizedName = name.trim();
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE subjects SET name = ? WHERE id = ?", normalizedName, subjectId);
            requireUpdated(updated, "Subject", subjectId);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate("Subject", normalizedName);
        }
        return new SubjectResponse(subjectId, normalizedName);
    }

    public void deleteSubject(int subjectId) {
        requireExists("subjects", subjectId, "Subject");
        if (count("SELECT COUNT(*) FROM quizzes WHERE subject_id = ?", subjectId) > 0) {
            throw new ResourceConflictException("Subject " + subjectId + " is used by a quiz");
        }
        jdbcTemplate.update("DELETE FROM subjects WHERE id = ?", subjectId);
    }

    public QuizResponse createQuiz(QuizRequest request) {
        validateQuizReferences(request);
        String name = request.name().trim();
        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO quizzes (name, time_to_pass, level_id, subject_id)
                        VALUES (?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, name);
                statement.setInt(2, request.timeToPassMinutes());
                statement.setInt(3, request.levelId());
                statement.setInt(4, request.subjectId());
                return statement;
            }, keyHolder);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate("Quiz", name);
        }
        return quiz(keyHolder.getKey().intValue());
    }

    public QuizResponse updateQuiz(int quizId, QuizRequest request) {
        validateQuizReferences(request);
        try {
            int updated = jdbcTemplate.update("""
                            UPDATE quizzes
                            SET name = ?, time_to_pass = ?, level_id = ?, subject_id = ?
                            WHERE id = ?
                            """,
                    request.name().trim(), request.timeToPassMinutes(), request.levelId(),
                    request.subjectId(), quizId);
            requireUpdated(updated, "Quiz", quizId);
        } catch (DataIntegrityViolationException exception) {
            throw duplicate("Quiz", request.name().trim());
        }
        return quiz(quizId);
    }

    @Transactional
    public void deleteQuiz(int quizId) {
        requireExists("quizzes", quizId, "Quiz");
        jdbcTemplate.update("""
                DELETE FROM results
                WHERE attempt_id IN (SELECT id FROM attempts WHERE quiz_id = ?)
                   OR answer_id IN (
                       SELECT answers.id FROM answers
                       JOIN questions ON questions.id = answers.question_id
                       WHERE questions.quiz_id = ?)
                """, quizId, quizId);
        jdbcTemplate.update("""
                DELETE FROM answers
                WHERE question_id IN (SELECT id FROM questions WHERE quiz_id = ?)
                """, quizId);
        jdbcTemplate.update("DELETE FROM attempts WHERE quiz_id = ?", quizId);
        jdbcTemplate.update("DELETE FROM questions WHERE quiz_id = ?", quizId);
        jdbcTemplate.update("DELETE FROM quizzes WHERE id = ?", quizId);
    }

    @Transactional
    public QuestionResponse createQuestion(int quizId, QuestionRequest request) {
        requireExists("quizzes", quizId, "Quiz");
        validateAnswers(request.answers());
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO questions (question, quiz_id) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.text().trim());
            statement.setInt(2, quizId);
            return statement;
        }, keyHolder);
        int questionId = keyHolder.getKey().intValue();
        insertAnswers(questionId, request.answers());
        return question(questionId);
    }

    @Transactional
    public QuestionResponse updateQuestion(int questionId, QuestionRequest request) {
        validateAnswers(request.answers());
        int updated = jdbcTemplate.update(
                "UPDATE questions SET question = ? WHERE id = ?", request.text().trim(), questionId);
        requireUpdated(updated, "Question", questionId);
        List<Integer> answerIds = jdbcTemplate.query(
                "SELECT id FROM answers WHERE question_id = ? ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getInt(1), questionId);
        if (answerIds.size() != request.answers().size()) {
            throw new ResourceConflictException(
                    "Question " + questionId + " does not contain exactly four answers");
        }
        for (int index = 0; index < answerIds.size(); index++) {
            AnswerRequest answer = request.answers().get(index);
            jdbcTemplate.update(
                    "UPDATE answers SET answer = ?, correct = ? WHERE id = ?",
                    answer.text().trim(), answer.correct(), answerIds.get(index));
        }
        return question(questionId);
    }

    @Transactional
    public void deleteQuestion(int questionId) {
        requireExists("questions", questionId, "Question");
        jdbcTemplate.update("""
                DELETE FROM results
                WHERE answer_id IN (SELECT id FROM answers WHERE question_id = ?)
                """, questionId);
        jdbcTemplate.update("DELETE FROM answers WHERE question_id = ?", questionId);
        jdbcTemplate.update("DELETE FROM questions WHERE id = ?", questionId);
    }

    public List<UserResponse> users() {
        return jdbcTemplate.query("""
                        SELECT users.id, users.login, roles.name AS role_name, statuses.name AS status_name
                        FROM users
                        JOIN roles ON roles.id = users.role_id
                        JOIN statuses ON statuses.id = users.status_id
                        ORDER BY users.login
                        """,
                (resultSet, rowNumber) -> new UserResponse(
                        resultSet.getInt("id"), resultSet.getString("login"),
                        resultSet.getString("role_name"), resultSet.getString("status_name")));
    }

    public UserResponse updateUserStatus(int userId, String status, String currentUsername) {
        String normalizedStatus = status.toLowerCase(java.util.Locale.ROOT);
        UserResponse user = user(userId);
        if (user.username().equals(currentUsername) && "blocked".equals(normalizedStatus)) {
            throw new ResourceConflictException("An administrator cannot block the current account");
        }
        int updated = jdbcTemplate.update("""
                        UPDATE users
                        SET status_id = (SELECT id FROM statuses WHERE LOWER(name) = ?)
                        WHERE id = ?
                        """, normalizedStatus, userId);
        requireUpdated(updated, "User", userId);
        return user(userId);
    }

    public List<ResultResponse> results(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("Result range start must not be after its end");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT attempts.id AS attempt_id, users.login, quizzes.id AS quiz_id,
                       quizzes.name AS quiz_name, attempts.score, attempts.end_time
                FROM attempts
                JOIN users ON users.id = attempts.user_id
                JOIN quizzes ON quizzes.id = attempts.quiz_id
                WHERE attempts.completed = TRUE AND attempts.end_time IS NOT NULL
                """);
        var parameters = new ArrayList<>();
        if (from != null) {
            sql.append(" AND attempts.end_time >= ?");
            parameters.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND attempts.end_time <= ?");
            parameters.add(Timestamp.from(to));
        }
        sql.append(" ORDER BY attempts.end_time DESC, attempts.id DESC");
        return jdbcTemplate.query(sql.toString(), (resultSet, rowNumber) -> new ResultResponse(
                resultSet.getLong("attempt_id"),
                resultSet.getString("login"),
                resultSet.getInt("quiz_id"),
                resultSet.getString("quiz_name"),
                resultSet.getInt("score"),
                resultSet.getTimestamp("end_time").toLocalDateTime().toInstant(ZoneOffset.UTC)),
                parameters.toArray());
    }

    private QuizResponse quiz(int quizId) {
        return quizzes().stream()
                .filter(quiz -> quiz.id() == quizId)
                .findFirst()
                .orElseThrow(() -> missing("Quiz", quizId));
    }

    private QuestionResponse question(int questionId) {
        Integer quizId = jdbcTemplate.query(
                        "SELECT quiz_id FROM questions WHERE id = ?",
                        (resultSet, rowNumber) -> resultSet.getInt(1), questionId)
                .stream().findFirst()
                .orElseThrow(() -> missing("Question", questionId));
        return questions(quizId).stream()
                .filter(question -> question.id() == questionId)
                .findFirst()
                .orElseThrow(() -> missing("Question", questionId));
    }

    private UserResponse user(int userId) {
        return users().stream()
                .filter(user -> user.id() == userId)
                .findFirst()
                .orElseThrow(() -> missing("User", userId));
    }

    private void validateQuizReferences(QuizRequest request) {
        requireExists("subjects", request.subjectId(), "Subject");
        requireExists("levels", request.levelId(), "Level");
    }

    private static void validateAnswers(List<AnswerRequest> answers) {
        if (answers.stream().noneMatch(AnswerRequest::correct)) {
            throw new InvalidRequestException("A question must have at least one correct answer");
        }
    }

    private void insertAnswers(int questionId, List<AnswerRequest> answers) {
        List<Object[]> rows = answers.stream()
                .map(answer -> new Object[]{answer.text().trim(), answer.correct(), questionId})
                .toList();
        jdbcTemplate.batchUpdate(
                "INSERT INTO answers (answer, correct, question_id) VALUES (?, ?, ?)", rows);
    }

    private void requireExists(String table, int id, String resource) {
        if (count("SELECT COUNT(*) FROM " + table + " WHERE id = ?", id) == 0) {
            throw missing(resource, id);
        }
    }

    private int count(String sql, int id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }

    private static void requireUpdated(int count, String resource, int id) {
        if (count == 0) {
            throw missing(resource, id);
        }
    }

    private static ResourceNotFoundException missing(String resource, int id) {
        return new ResourceNotFoundException(resource + " " + id + " was not found");
    }

    private static ResourceConflictException duplicate(String resource, String name) {
        return new ResourceConflictException(resource + " named '" + name + "' already exists");
    }

    private record MutableQuestion(String text, List<AnswerResponse> answers) {
        private MutableQuestion(String text) {
            this(text, new ArrayList<>());
        }
    }
}
