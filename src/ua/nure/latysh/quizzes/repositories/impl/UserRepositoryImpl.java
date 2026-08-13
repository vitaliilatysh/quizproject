package ua.nure.latysh.quizzes.repositories.impl;

import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;
import ua.nure.latysh.quizzes.repositories.UserRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserRepositoryImpl implements UserRepository {
    private final DbConnector dbConnector;

    public UserRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    UserRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Optional<User> findById(int userId) {
        return findOne("SELECT * FROM users WHERE id = ?", statement -> statement.setInt(1, userId));
    }

    @Override
    public void delete(User user) {
        execute("DELETE FROM users WHERE id = ?", statement -> statement.setInt(1, user.getId()), "delete user");
    }

    @Override
    public boolean save(User user) {
        execute("INSERT INTO users (login, password, first_name, last_name, register_date, status_id, role_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", statement -> {
            statement.setString(1, user.getLogin());
            statement.setString(2, user.getPassword());
            statement.setString(3, user.getFirstName());
            statement.setString(4, user.getLastName());
            statement.setTimestamp(5, new Timestamp(user.getRegisterDateTime().getTime()));
            statement.setInt(6, user.getStatusId());
            statement.setInt(7, user.getRoleId());
        }, "save user");
        return true;
    }

    @Override
    public void update(User user) {
        execute("UPDATE users SET status_id = ? WHERE id = ?", statement -> {
            statement.setInt(1, user.getStatusId());
            statement.setInt(2, user.getId());
        }, "update user");
    }

    @Override
    public void updateLoginDate(User user) {
        execute("UPDATE users SET login_date = ? WHERE id = ?", statement -> {
            statement.setTimestamp(1, new Timestamp(user.getLoginDateTime().getTime()));
            statement.setInt(2, user.getId());
        }, "update user login date");
    }

    @Override
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE role_id <> 1")) {
            while (resultSet.next()) {
                users.add(extractUser(resultSet));
            }
            return users;
        } catch (SQLException exception) {
            throw failure("list users", exception);
        }
    }

    @Override
    public void updatePassword(User user) {
        execute("UPDATE users SET password = ? WHERE id = ?", statement -> {
            statement.setString(1, user.getPassword());
            statement.setInt(2, user.getId());
        }, "update user password");
    }

    @Override
    public Optional<User> findByLogin(String login) {
        return findOne("SELECT * FROM users WHERE login = ?", statement -> statement.setString(1, login));
    }

    private Optional<User> findOne(String sql, StatementConfigurer configurer) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(extractUser(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("find user", exception);
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

    private User extractUser(ResultSet resultSet) throws SQLException {
        User user = new User();
        user.setId(resultSet.getInt("id"));
        user.setLogin(resultSet.getString("login"));
        user.setFirstName(resultSet.getString("first_name"));
        user.setLastName(resultSet.getString("last_name"));
        user.setPassword(resultSet.getString("password"));
        user.setRegisterDateTime(resultSet.getTimestamp("register_date"));
        user.setLoginDateTime(resultSet.getTimestamp("login_date"));
        user.setStatusId(resultSet.getInt("status_id"));
        user.setRoleId(resultSet.getInt("role_id"));
        return user;
    }

    private RepositoryException failure(String operation, SQLException exception) {
        return new RepositoryException("Could not " + operation, exception);
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
