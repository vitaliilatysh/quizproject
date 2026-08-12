package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Result;
import ua.nure.latysh.quizzes.repositories.ResultRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ResultRepositoryImpl implements ResultRepository {

    private static final Logger logger = Logger.getLogger(ResultRepositoryImpl.class);
    private final DbConnector dbConnector;

    public ResultRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    ResultRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Result findById(int resultId) {
        Result result = new Result();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM results WHERE id = ?")) {
            statement.setInt(1, resultId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    result = extractResult(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return result;
    }

    @Override
    public void delete(Result result) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM results WHERE id = ?")) {
            statement.setInt(1, result.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public boolean save(Result result) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO results (answer_id, attempt_id) VALUES (?, ?)")) {
            statement.setInt(1, result.getAnswerId());
            statement.setInt(2, result.getAttemptId());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Result result) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE results SET answer_id = ?, attempt_id = ? WHERE id = ?")) {
            statement.setInt(1, result.getAnswerId());
            statement.setInt(2, result.getAttemptId());
            statement.setInt(3, result.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public List<Result> findAll() {
        List<Result> results = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM results")) {
            while (resultSet.next()) {
                results.add(extractResult(resultSet));
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return results;
    }

    private Result extractResult(ResultSet rs)
            throws SQLException {
        Result result = new Result();
        result.setId(rs.getInt("id"));
        result.setAnswerId(rs.getInt("answer_id"));
        result.setAttemptId(rs.getInt("attempt_id"));
        return result;
    }

    @Override
    public List<Result> findByAttemptId(int attemptId) {
        List<Result> results = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM results WHERE attempt_id = ?")) {
            statement.setInt(1, attemptId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(extractResult(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return results;
    }

    @Override
    public List<Result> findAllByUserId(int userId) {
        List<Result> results = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT results.* FROM results JOIN attempts ON results.attempt_id = attempts.id "
                             + "WHERE attempts.user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(extractResult(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return results;
    }
}
