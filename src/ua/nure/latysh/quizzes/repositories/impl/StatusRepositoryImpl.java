package ua.nure.latysh.quizzes.repositories.impl;

import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Status;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;
import ua.nure.latysh.quizzes.repositories.StatusRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StatusRepositoryImpl implements StatusRepository {
    private final DbConnector dbConnector;

    public StatusRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    StatusRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Optional<Status> findById(int statusId) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM statuses WHERE id = ?")) {
            statement.setInt(1, statusId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(extractStatus(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("find status by id", exception);
        }
    }

    @Override
    public void delete(Status status) {
        execute("DELETE FROM statuses WHERE id = ?", statement -> statement.setInt(1, status.getId()),
                "delete status");
    }

    @Override
    public boolean save(Status status) {
        execute("INSERT INTO statuses (name) VALUES (?)", statement -> statement.setString(1, status.getStatus()),
                "save status");
        return true;
    }

    @Override
    public void update(Status status) {
        execute("UPDATE statuses SET name = ? WHERE id = ?", statement -> {
            statement.setString(1, status.getStatus());
            statement.setInt(2, status.getId());
        }, "update status");
    }

    @Override
    public List<Status> findAll() {
        List<Status> statuses = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM statuses")) {
            while (resultSet.next()) {
                statuses.add(extractStatus(resultSet));
            }
            return statuses;
        } catch (SQLException exception) {
            throw failure("list statuses", exception);
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

    private Status extractStatus(ResultSet resultSet) throws SQLException {
        Status status = new Status();
        status.setId(resultSet.getInt("id"));
        status.setStatus(resultSet.getString("name"));
        return status;
    }

    private RepositoryException failure(String operation, SQLException exception) {
        return new RepositoryException("Could not " + operation, exception);
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
