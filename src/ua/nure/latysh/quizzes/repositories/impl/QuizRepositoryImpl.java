package ua.nure.latysh.quizzes.repositories.impl;

import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;
import ua.nure.latysh.quizzes.repositories.QuizRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuizRepositoryImpl implements QuizRepository {
    private static final String SELECT_ALL = "SELECT * FROM quizzes";
    private static final String SELECT_BY_ID = "SELECT * FROM quizzes WHERE id = ?";
    private static final String SELECT_BY_NAME = "SELECT * FROM quizzes WHERE name = ?";
    private static final String SELECT_BY_SUBJECT_ID = "SELECT * FROM quizzes WHERE subject_id = ?";
    private static final String SELECT_BY_SUBJECT_NAME =
            "SELECT q.id, q.name, q.time_to_pass, q.level_id, q.subject_id "
                    + "FROM quizzes q JOIN subjects s ON q.subject_id = s.id WHERE s.name LIKE ?";
    private static final String INSERT =
            "INSERT INTO quizzes (name, time_to_pass, level_id, subject_id) VALUES (?, ?, ?, ?)";
    private static final String UPDATE =
            "UPDATE quizzes SET name = ?, time_to_pass = ?, level_id = ?, subject_id = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM quizzes WHERE id = ?";

    private final DbConnector dbConnector;

    public QuizRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    QuizRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Optional<Quiz> findByName(String quizName) {
        return findOne(SELECT_BY_NAME, statement -> statement.setString(1, quizName));
    }

    @Override
    public List<Quiz> findBySubjectId(int subjectId) {
        return findMany(SELECT_BY_SUBJECT_ID, statement -> statement.setInt(1, subjectId));
    }

    @Override
    public List<Quiz> findBySubjectName(String subjectName) {
        return findMany(SELECT_BY_SUBJECT_NAME, statement -> statement.setString(1, "%" + subjectName + "%"));
    }

    @Override
    public Optional<Quiz> findById(int quizId) {
        return findOne(SELECT_BY_ID, statement -> statement.setInt(1, quizId));
    }

    @Override
    public void delete(Quiz quiz) {
        execute(DELETE, statement -> statement.setInt(1, quiz.getId()), "delete quiz");
    }

    @Override
    public boolean save(Quiz quiz) {
        execute(INSERT, statement -> setQuizFields(statement, quiz), "save quiz");
        return true;
    }

    @Override
    public void update(Quiz quiz) {
        execute(UPDATE, statement -> {
            setQuizFields(statement, quiz);
            statement.setInt(5, quiz.getId());
        }, "update quiz");
    }

    @Override
    public List<Quiz> findAll() {
        List<Quiz> quizzes = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_ALL)) {
            while (resultSet.next()) {
                quizzes.add(extractQuiz(resultSet));
            }
            return quizzes;
        } catch (SQLException exception) {
            throw failure("list quizzes", exception);
        }
    }

    private Optional<Quiz> findOne(String sql, StatementConfigurer configurer) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(extractQuiz(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("find quiz", exception);
        }
    }

    private List<Quiz> findMany(String sql, StatementConfigurer configurer) {
        List<Quiz> quizzes = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    quizzes.add(extractQuiz(resultSet));
                }
            }
            return quizzes;
        } catch (SQLException exception) {
            throw failure("list quizzes", exception);
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

    private void setQuizFields(PreparedStatement statement, Quiz quiz) throws SQLException {
        statement.setString(1, quiz.getName());
        statement.setInt(2, quiz.getTimeToPass());
        statement.setInt(3, quiz.getLevelId());
        statement.setInt(4, quiz.getSubjectId());
    }

    private Quiz extractQuiz(ResultSet resultSet) throws SQLException {
        Quiz quiz = new Quiz();
        quiz.setId(resultSet.getInt("id"));
        quiz.setName(resultSet.getString("name"));
        quiz.setTimeToPass(resultSet.getInt("time_to_pass"));
        quiz.setLevelId(resultSet.getInt("level_id"));
        quiz.setSubjectId(resultSet.getInt("subject_id"));
        return quiz;
    }

    private RepositoryException failure(String operation, SQLException exception) {
        return new RepositoryException("Could not " + operation, exception);
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
