package ua.nure.latysh.quizzes.repositories.impl;

import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuestionRepositoryImpl implements QuestionRepository {
    private static final String INSERT_QUESTION = "INSERT INTO questions (question, quiz_id) VALUES (?, ?)";
    private static final String INSERT_ANSWER =
            "INSERT INTO answers (answer, correct, question_id) VALUES (?, ?, ?)";
    private static final String UPDATE_QUESTION =
            "UPDATE questions SET question = ?, quiz_id = ? WHERE id = ?";
    private static final String UPDATE_ANSWER =
            "UPDATE answers SET answer = ?, correct = ? WHERE id = ? AND question_id = ?";

    private final DbConnector dbConnector;

    public QuestionRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    QuestionRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Optional<Question> findById(int questionId) {
        return findOne("SELECT * FROM questions WHERE id = ?", statement -> statement.setInt(1, questionId));
    }

    @Override
    public void delete(Question question) {
        execute("DELETE FROM questions WHERE id = ?", statement -> statement.setInt(1, question.getId()),
                "delete question");
    }

    @Override
    public Question saveQuestion(Question question) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     INSERT_QUESTION, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, question.getQuestion());
            statement.setInt(2, question.getQuizId());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException("Question insert returned no generated id", null);
                }
                Question savedQuestion = new Question();
                savedQuestion.setId(generatedKeys.getInt(1));
                savedQuestion.setQuestion(question.getQuestion());
                savedQuestion.setQuizId(question.getQuizId());
                return savedQuestion;
            }
        } catch (SQLException exception) {
            throw failure("save question", exception);
        }
    }

    @Override
    public Question createWithAnswers(Question question, List<Answer> answers) {
        try {
            try (Connection connection = dbConnector.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Question savedQuestion = insertQuestion(connection, question);
                    insertAnswers(connection, savedQuestion.getId(), answers);
                    connection.commit();
                    return savedQuestion;
                } catch (RepositoryException exception) {
                    rollback(connection, exception);
                    throw exception;
                } catch (SQLException exception) {
                    RepositoryException failure = failure("create question with answers", exception);
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException exception) {
            throw failure("close question transaction", exception);
        }
    }

    @Override
    public void updateWithAnswers(Question question, List<Answer> answers) {
        try {
            try (Connection connection = dbConnector.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    updateQuestion(connection, question);
                    updateAnswers(connection, question.getId(), answers);
                    connection.commit();
                } catch (RepositoryException exception) {
                    rollback(connection, exception);
                    throw exception;
                } catch (SQLException exception) {
                    RepositoryException failure = failure("update question with answers", exception);
                    rollback(connection, failure);
                    throw failure;
                }
            }
        } catch (SQLException exception) {
            throw failure("close question transaction", exception);
        }
    }

    @Override
    public boolean save(Question question) {
        saveQuestion(question);
        return true;
    }

    @Override
    public void update(Question question) {
        execute(UPDATE_QUESTION, statement -> {
            statement.setString(1, question.getQuestion());
            statement.setInt(2, question.getQuizId());
            statement.setInt(3, question.getId());
        }, "update question");
    }

    private Question insertQuestion(Connection connection, Question question) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_QUESTION, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, question.getQuestion());
            statement.setInt(2, question.getQuizId());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException("Question insert returned no generated id", null);
                }
                Question savedQuestion = new Question();
                savedQuestion.setId(generatedKeys.getInt(1));
                savedQuestion.setQuestion(question.getQuestion());
                savedQuestion.setQuizId(question.getQuizId());
                return savedQuestion;
            }
        }
    }

    private void insertAnswers(Connection connection, int questionId, List<Answer> answers) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ANSWER)) {
            for (Answer answer : answers) {
                statement.setString(1, answer.getAnswer());
                statement.setBoolean(2, answer.isCorrect());
                statement.setInt(3, questionId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void updateQuestion(Connection connection, Question question) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_QUESTION)) {
            statement.setString(1, question.getQuestion());
            statement.setInt(2, question.getQuizId());
            statement.setInt(3, question.getId());
            if (statement.executeUpdate() != 1) {
                throw new RepositoryException("Question not found: " + question.getId(), null);
            }
        }
    }

    private void updateAnswers(Connection connection, int questionId, List<Answer> answers) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_ANSWER)) {
            for (Answer answer : answers) {
                statement.setString(1, answer.getAnswer());
                statement.setBoolean(2, answer.isCorrect());
                statement.setInt(3, answer.getId());
                statement.setInt(4, questionId);
                statement.addBatch();
            }
            int[] updatedRows = statement.executeBatch();
            if (updatedRows.length != answers.size()) {
                throw new RepositoryException("Could not update every answer for question " + questionId, null);
            }
            for (int updatedRow : updatedRows) {
                if (updatedRow == 0 || updatedRow == Statement.EXECUTE_FAILED) {
                    throw new RepositoryException("Answer does not belong to question " + questionId, null);
                }
            }
        }
    }

    private void rollback(Connection connection, RuntimeException failure) {
        try {
            dbConnector.rollback(connection);
        } catch (RepositoryException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    @Override
    public List<Question> findAll() {
        List<Question> questions = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM questions")) {
            while (resultSet.next()) {
                questions.add(extractQuestion(resultSet));
            }
            return questions;
        } catch (SQLException exception) {
            throw failure("list questions", exception);
        }
    }

    @Override
    public List<Question> findAllByQuizId(int quizId) {
        List<Question> questions = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, question, quiz_id FROM questions WHERE quiz_id = ?")) {
            statement.setInt(1, quizId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    questions.add(extractQuestion(resultSet));
                }
            }
            return questions;
        } catch (SQLException exception) {
            throw failure("list questions by quiz", exception);
        }
    }

    @Override
    public Optional<Question> findByName(String questionName) {
        return findOne("SELECT * FROM questions WHERE question = ?",
                statement -> statement.setString(1, questionName));
    }

    private Optional<Question> findOne(String sql, StatementConfigurer configurer) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(extractQuestion(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("find question", exception);
        }
    }

    private void execute(String sql, StatementConfigurer configurer, String operation) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw failure(operation, exception);
        }
    }

    private Question extractQuestion(ResultSet resultSet) throws SQLException {
        Question question = new Question();
        question.setId(resultSet.getInt("id"));
        question.setQuestion(resultSet.getString("question"));
        question.setQuizId(resultSet.getInt("quiz_id"));
        return question;
    }

    private RepositoryException failure(String operation, SQLException exception) {
        return new RepositoryException("Could not " + operation, exception);
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
