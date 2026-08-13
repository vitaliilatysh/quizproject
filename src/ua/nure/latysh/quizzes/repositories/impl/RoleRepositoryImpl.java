package ua.nure.latysh.quizzes.repositories.impl;

import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Role;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;
import ua.nure.latysh.quizzes.repositories.RoleRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoleRepositoryImpl implements RoleRepository {
    private final DbConnector dbConnector;

    public RoleRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    RoleRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Optional<Role> findById(int roleId) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM roles WHERE id = ?")) {
            statement.setInt(1, roleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(extractRole(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("find role by id", exception);
        }
    }

    @Override
    public void delete(Role role) {
        execute("DELETE FROM roles WHERE id = ?", statement -> statement.setInt(1, role.getId()), "delete role");
    }

    @Override
    public boolean save(Role role) {
        execute("INSERT INTO roles (name) VALUES (?)", statement -> statement.setString(1, role.getRole()),
                "save role");
        return true;
    }

    @Override
    public void update(Role role) {
        execute("UPDATE roles SET name = ? WHERE id = ?", statement -> {
            statement.setString(1, role.getRole());
            statement.setInt(2, role.getId());
        }, "update role");
    }

    @Override
    public List<Role> findAll() {
        List<Role> roles = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM roles")) {
            while (resultSet.next()) {
                roles.add(extractRole(resultSet));
            }
            return roles;
        } catch (SQLException exception) {
            throw failure("list roles", exception);
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

    private Role extractRole(ResultSet resultSet) throws SQLException {
        Role role = new Role();
        role.setId(resultSet.getInt("id"));
        role.setRole(resultSet.getString("name"));
        return role;
    }

    private RepositoryException failure(String operation, SQLException exception) {
        return new RepositoryException("Could not " + operation, exception);
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
