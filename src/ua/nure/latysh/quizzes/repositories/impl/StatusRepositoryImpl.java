package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Status;
import ua.nure.latysh.quizzes.repositories.StatusRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class StatusRepositoryImpl implements StatusRepository {

    private static final Logger logger = Logger.getLogger(StatusRepositoryImpl.class);
    private final DbConnector dbConnector;

    public StatusRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    StatusRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Status findById(int statusId) {
        Status status = new Status();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM statuses WHERE id = ?")) {
            statement.setInt(1, statusId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    status = extractStatus(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return status;
    }


    @Override
    public void delete(Status status) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM statuses WHERE id = ?")) {
            statement.setInt(1, status.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public boolean save(Status status) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO statuses (name) VALUES (?)")) {
            statement.setString(1, status.getStatus());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Status status) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE statuses SET name = ? WHERE id = ?")) {
            statement.setString(1, status.getStatus());
            statement.setInt(2, status.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
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
        } catch (SQLException e) {
            logger.error(e);
        }
        return statuses;
    }

    private Status extractStatus(ResultSet rs)
            throws SQLException {
        Status status = new Status();
        status.setId(rs.getInt("id"));
        status.setStatus(rs.getString("name"));
        return status;
    }

}
