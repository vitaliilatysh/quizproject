package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Role;
import ua.nure.latysh.quizzes.repositories.RoleRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RoleRepositoryImpl implements RoleRepository {

    private static final Logger logger = Logger.getLogger(RoleRepositoryImpl.class);
    private final DbConnector dbConnector;

    public RoleRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    RoleRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Role findById(int roleId) {
        Role role = new Role();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM roles WHERE id = ?")) {
            statement.setInt(1, roleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    role = extractRole(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return role;
    }

    @Override
    public void delete(Role role) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM roles WHERE id = ?")) {
            statement.setInt(1, role.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public boolean save(Role role) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO roles (name) VALUES (?)")) {
            statement.setString(1, role.getRole());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Role role) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE roles SET name = ? WHERE id = ?")) {
            statement.setString(1, role.getRole());
            statement.setInt(2, role.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
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
        } catch (SQLException e) {
            logger.error(e);
        }
        return roles;
    }

    private Role extractRole(ResultSet rs)
            throws SQLException {
        Role role = new Role();
        role.setId(rs.getInt("id"));
        role.setRole(rs.getString("name"));
        return role;
    }

}
