package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.exceptions.QuizSubmissionException;
import ua.nure.latysh.quizzes.repositories.AttemptRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AttemptRepositoryImpl implements AttemptRepository {
    private static final Logger logger = Logger.getLogger(AttemptRepositoryImpl.class);
    private static final String INSERT_ATTEMPT =
            "INSERT INTO attempts (score, start_time, expires_at, quiz_id, user_id, completed) VALUES (?,?,?,?,?,?)";
    private static final String SELECT_ATTEMPT_FOR_UPDATE =
            "SELECT * FROM attempts WHERE id=? AND user_id=? FOR UPDATE";
    private static final String SELECT_QUIZ_ANSWERS =
            "SELECT q.id AS question_id, a.id AS answer_id, a.correct "
                    + "FROM questions q JOIN answers a ON a.question_id=q.id WHERE q.quiz_id=?";
    private static final String INSERT_RESULT =
            "INSERT INTO results (answer_id, attempt_id) VALUES (?,?)";
    private static final String COMPLETE_ATTEMPT =
            "UPDATE attempts SET score=?, end_time=?, completed=TRUE "
                    + "WHERE id=? AND user_id=? AND completed=FALSE";

    private final DbConnector dbConnector;

    public AttemptRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    AttemptRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Attempt findById(int attemptId) {
        Attempt attempt = new Attempt();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM attempts WHERE id=?")) {
            statement.setInt(1, attemptId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    attempt = extractAttempt(resultSet);
                }
            }
        } catch (SQLException exception) {
            logger.error(exception);
        }
        return attempt;
    }

    @Override
    public void delete(Attempt attempt) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM attempts WHERE id=?")) {
            statement.setInt(1, attempt.getId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            logger.error(exception);
        }
    }

    @Override
    public boolean save(Attempt attempt) {
        try {
            create(attempt);
            return true;
        } catch (IllegalStateException exception) {
            logger.error(exception);
            return false;
        }
    }

    @Override
    public Attempt create(Attempt attempt) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_ATTEMPT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, attempt.getScore());
            statement.setTimestamp(2, new Timestamp(attempt.getStartTime().getTime()));
            statement.setTimestamp(3, new Timestamp(attempt.getExpiresAt().getTime()));
            statement.setInt(4, attempt.getQuizId());
            statement.setInt(5, attempt.getUserId());
            statement.setBoolean(6, attempt.isCompleted());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new IllegalStateException("Attempt insert returned no generated id");
                }
                attempt.setId(generatedKeys.getInt(1));
                return attempt;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create attempt", exception);
        }
    }

    @Override
    public void update(Attempt attempt) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE attempts SET score=?, end_time=?, completed=? WHERE id=?")) {
            statement.setInt(1, attempt.getScore());
            statement.setTimestamp(2, new Timestamp(attempt.getEndTime().getTime()));
            statement.setBoolean(3, attempt.isCompleted());
            statement.setInt(4, attempt.getId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            logger.error(exception);
        }
    }

    @Override
    public List<Attempt> findAll() {
        return findMany("SELECT * FROM attempts", null, null);
    }

    @Override
    public List<Attempt> findAllByUserId(int userId) {
        return findMany("SELECT * FROM attempts WHERE user_id=?", userId, null);
    }

    @Override
    public Attempt findLastByUserId(int userId) {
        Attempt attempt = new Attempt();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM attempts WHERE user_id=? ORDER BY id DESC LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    attempt = extractAttempt(resultSet);
                }
            }
        } catch (SQLException exception) {
            logger.error(exception);
        }
        return attempt;
    }

    @Override
    public List<Attempt> findAllBetweenFinishDates(String startRange, String endRange) {
        return findMany("SELECT * FROM attempts WHERE end_time BETWEEN ? AND ?", startRange, endRange);
    }

    @Override
    public Attempt complete(int attemptId, int userId, Set<Integer> answerIds, Date completedAt) {
        Connection connection = dbConnector.getConnection();
        if (connection == null) {
            throw new IllegalStateException("Database connection is unavailable");
        }
        try {
            connection.setAutoCommit(false);
            Attempt attempt = lockAttempt(connection, attemptId, userId);
            validateActiveAttempt(attempt, completedAt);
            QuizAnswerData answerData = loadQuizAnswers(connection, attempt.getQuizId());
            validateAnswers(answerData, answerIds);
            int score = calculateScore(answerData, answerIds);
            insertResults(connection, attemptId, answerIds);
            markCompleted(connection, attemptId, userId, score, completedAt);
            connection.commit();
            attempt.setScore(score);
            attempt.setEndTime(completedAt);
            attempt.setCompleted(true);
            return attempt;
        } catch (QuizSubmissionException exception) {
            dbConnector.rollback(connection);
            throw exception;
        } catch (SQLException exception) {
            dbConnector.rollback(connection);
            throw new IllegalStateException("Could not complete attempt", exception);
        } finally {
            dbConnector.close(connection, null, null);
        }
    }

    private Attempt lockAttempt(Connection connection, int attemptId, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ATTEMPT_FOR_UPDATE)) {
            statement.setInt(1, attemptId);
            statement.setInt(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new QuizSubmissionException(QuizSubmissionException.Reason.NOT_FOUND,
                            "Attempt does not belong to the current user");
                }
                return extractAttempt(resultSet);
            }
        }
    }

    private void validateActiveAttempt(Attempt attempt, Date completedAt) {
        if (attempt.isCompleted()) {
            throw new QuizSubmissionException(QuizSubmissionException.Reason.ALREADY_COMPLETED,
                    "Attempt was already completed");
        }
        if (attempt.getExpiresAt() == null || completedAt.after(attempt.getExpiresAt())) {
            throw new QuizSubmissionException(QuizSubmissionException.Reason.EXPIRED,
                    "Attempt has expired");
        }
    }

    private QuizAnswerData loadQuizAnswers(Connection connection, int quizId) throws SQLException {
        QuizAnswerData data = new QuizAnswerData();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_QUIZ_ANSWERS)) {
            statement.setInt(1, quizId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int questionId = resultSet.getInt("question_id");
                    int answerId = resultSet.getInt("answer_id");
                    data.correctAnswers.computeIfAbsent(questionId, ignored -> new HashSet<>());
                    data.answerToQuestion.put(answerId, questionId);
                    if (resultSet.getBoolean("correct")) {
                        data.correctAnswers.get(questionId).add(answerId);
                    }
                }
            }
        }
        return data;
    }

    private void validateAnswers(QuizAnswerData data, Set<Integer> answerIds) {
        if (!data.answerToQuestion.keySet().containsAll(answerIds)) {
            throw new QuizSubmissionException(QuizSubmissionException.Reason.INVALID_ANSWER,
                    "An answer does not belong to the attempted quiz");
        }
    }

    private int calculateScore(QuizAnswerData data, Set<Integer> answerIds) {
        if (data.correctAnswers.isEmpty()) {
            return 0;
        }
        Map<Integer, Set<Integer>> selectedByQuestion = new HashMap<>();
        for (Integer answerId : answerIds) {
            int questionId = data.answerToQuestion.get(answerId);
            selectedByQuestion.computeIfAbsent(questionId, ignored -> new HashSet<>()).add(answerId);
        }

        int correctQuestions = 0;
        for (Map.Entry<Integer, Set<Integer>> entry : data.correctAnswers.entrySet()) {
            Set<Integer> selected = selectedByQuestion.getOrDefault(entry.getKey(), Set.of());
            if (!entry.getValue().isEmpty() && selected.equals(entry.getValue())) {
                correctQuestions++;
            }
        }
        return (int) (correctQuestions * 100.0f / data.correctAnswers.size());
    }

    private void insertResults(Connection connection, int attemptId, Set<Integer> answerIds) throws SQLException {
        if (answerIds.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RESULT)) {
            for (Integer answerId : answerIds) {
                statement.setInt(1, answerId);
                statement.setInt(2, attemptId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void markCompleted(Connection connection, int attemptId, int userId, int score, Date completedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COMPLETE_ATTEMPT)) {
            statement.setInt(1, score);
            statement.setTimestamp(2, new Timestamp(completedAt.getTime()));
            statement.setInt(3, attemptId);
            statement.setInt(4, userId);
            if (statement.executeUpdate() != 1) {
                throw new QuizSubmissionException(QuizSubmissionException.Reason.ALREADY_COMPLETED,
                        "Attempt completion lost a concurrent update");
            }
        }
    }

    private List<Attempt> findMany(String sql, Object firstParameter, Object secondParameter) {
        List<Attempt> attempts = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (firstParameter instanceof Integer integer) {
                statement.setInt(1, integer);
            } else if (firstParameter instanceof String string) {
                statement.setString(1, string);
                statement.setString(2, (String) secondParameter);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    attempts.add(extractAttempt(resultSet));
                }
            }
        } catch (SQLException exception) {
            logger.error(exception);
        }
        return attempts;
    }

    private Attempt extractAttempt(ResultSet resultSet) throws SQLException {
        Attempt attempt = new Attempt();
        attempt.setId(resultSet.getInt("id"));
        attempt.setScore(resultSet.getInt("score"));
        attempt.setStartTime(resultSet.getTimestamp("start_time"));
        attempt.setExpiresAt(resultSet.getTimestamp("expires_at"));
        attempt.setEndTime(resultSet.getTimestamp("end_time"));
        attempt.setQuizId(resultSet.getInt("quiz_id"));
        attempt.setUserId(resultSet.getInt("user_id"));
        attempt.setCompleted(resultSet.getBoolean("completed"));
        return attempt;
    }

    private static final class QuizAnswerData {
        private final Map<Integer, Set<Integer>> correctAnswers = new HashMap<>();
        private final Map<Integer, Integer> answerToQuestion = new HashMap<>();
    }
}
