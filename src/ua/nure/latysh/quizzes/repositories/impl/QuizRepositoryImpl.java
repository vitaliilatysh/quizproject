package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.repositories.QuizRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class QuizRepositoryImpl implements QuizRepository {

    private static final Logger logger = Logger.getLogger(QuizRepositoryImpl.class);
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
    public Quiz findByName(String quizName) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_NAME)) {
            statement.setString(1, quizName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Quiz quiz = extractQuiz(resultSet);
                    logger.info("Found " + quizName);
                    return quiz;
                }
            }
        } catch (SQLException ex) {
            logger.error(ex);
        }
        return new Quiz();
    }

    @Override
    public List<Quiz> findBySubjectId(int subjectId) {
        List<Quiz> quizzes = findMany(SELECT_BY_SUBJECT_ID, statement -> statement.setInt(1, subjectId));
        logger.info("Found " + quizzes.size() + " by subjectId:" + subjectId);
        return quizzes;
    }

    @Override
    public List<Quiz> findBySubjectName(String subjectName) {
        List<Quiz> quizzes = findMany(SELECT_BY_SUBJECT_NAME,
                statement -> statement.setString(1, "%" + subjectName + "%"));
        logger.info("Found " + quizzes.size() + " by subject name:" + subjectName);
        return quizzes;
    }

    @Override
    public Quiz findById(int quizId) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setInt(1, quizId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Quiz quiz = extractQuiz(resultSet);
                    logger.info("Found " + quiz.getName() + " by quiz id:" + quizId);
                    return quiz;
                }
            }
        } catch (SQLException ex) {
            logger.error(ex);
        }
        return new Quiz();
    }

    @Override
    public void delete(Quiz quiz) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setInt(1, quiz.getId());
            statement.executeUpdate();
            logger.info("Deleted " + quiz.getName());
        } catch (SQLException ex) {
            logger.error(ex);
        }
    }

    @Override
    public boolean save(Quiz quiz) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            setQuizFields(statement, quiz);
            statement.executeUpdate();
            logger.info("Saved " + quiz.getName());
            return true;
        } catch (SQLException ex) {
            logger.error(ex);
            return false;
        }
    }

    @Override
    public void update(Quiz quiz) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            setQuizFields(statement, quiz);
            statement.setInt(5, quiz.getId());
            statement.executeUpdate();
            logger.info("Updated " + quiz.getName());
        } catch (SQLException ex) {
            logger.error(ex);
        }
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
            logger.info("Found " + quizzes.size() + " quizzes");
        } catch (SQLException ex) {
            logger.error(ex);
        }
        return quizzes;
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
        } catch (SQLException ex) {
            logger.error(ex);
        }
        return quizzes;
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

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
