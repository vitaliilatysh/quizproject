package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Role;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;
import ua.nure.latysh.quizzes.repositories.RoleRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RoleRepositoryImpl implements RoleRepository {

    private final static Logger logger = Logger.getLogger(RoleRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

    @Override
    public Role findById(int roleId) {
        Role role = new Role();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from roles where id=?");
            statement.setInt(1, roleId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                role = extractRole(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return role;
    }

    @Override
    public void delete(Role answer) {
//        if (findById(subject.getId()) != null) {
//            try (Connection connection = dbConnector.getConnection()) {
//                PreparedStatement statement = connection.prepareStatement("DELETE FROM subjects WHERE id=?");
//                statement.setInt(1, subject.getId());
//                statement.executeUpdate();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//        }
    }

    @Override
    public boolean save(Role role) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO roles (id, role) VALUES (?, ?)");
            statement.setInt(1, role.getId());
            statement.setString(2, role.getRole());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Role answer) {
//        try (Connection connection = dbConnector.getConnection()) {
//            PreparedStatement statement = connection.prepareStatement("UPDATE subjects SET name=? WHERE id=?");
//            statement.setString(1, subject.getName());
//            statement.setInt(2, subject.getId());
//            statement.executeUpdate();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public List<Role> findAll() {
        List<Role> roles = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from roles");
            while (rs.next()) {
                roles.add(extractRole(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
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
