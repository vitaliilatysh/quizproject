package ua.nure.latysh.quizzes.repositories.impl;

import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Result;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;
import ua.nure.latysh.quizzes.repositories.ResultRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ResultRepositoryImpl implements ResultRepository {
    private final DbConnector dbConnector;

    public ResultRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    ResultRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Optional<Result> findById(int resultId) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM results WHERE id = ?")) {
            statement.setInt(1, resultId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(extractResult(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("find result by id", exception);
        }
    }

    @Override
    public void delete(Result result) {
        execute("DELETE FROM results WHERE id = ?", statement -> statement.setInt(1, result.getId()),
                "delete result");
    }

    @Override
    public boolean save(Result result) {
        execute("INSERT INTO results (answer_id, attempt_id) VALUES (?, ?)", statement -> {
            statement.setInt(1, result.getAnswerId());
            statement.setInt(2, result.getAttemptId());
        }, "save result");
        return true;
    }

    @Override
    public void update(Result result) {
        execute("UPDATE results SET answer_id = ?, attempt_id = ? WHERE id = ?", statement -> {
            statement.setInt(1, result.getAnswerId());
            statement.setInt(2, result.getAttemptId());
            statement.setInt(3, result.getId());
        }, "update result");
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
            return results;
        } catch (SQLException exception) {
            throw failure("list results", exception);
        }
    }

    @Override
    public List<Result> findByAttemptId(int attemptId) {
        return findMany("SELECT * FROM results WHERE attempt_id = ?", statement -> statement.setInt(1, attemptId));
    }

    @Override
    public List<Result> findAllByUserId(int userId) {
        return findMany("SELECT results.* FROM results JOIN attempts ON results.attempt_id = attempts.id "
                        + "WHERE attempts.user_id = ?", statement -> statement.setInt(1, userId));
    }

    private List<Result> findMany(String sql, StatementConfigurer configurer) {
        List<Result> results = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(extractResult(resultSet));
                }
            }
            return results;
        } catch (SQLException exception) {
            throw failure("list results", exception);
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

    private Result extractResult(ResultSet resultSet) throws SQLException {
        Result result = new Result();
        result.setId(resultSet.getInt("id"));
        result.setAnswerId(resultSet.getInt("answer_id"));
        result.setAttemptId(resultSet.getInt("attempt_id"));
        return result;
    }

    private RepositoryException failure(String operation, SQLException exception) {
        return new RepositoryException("Could not " + operation, exception);
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
