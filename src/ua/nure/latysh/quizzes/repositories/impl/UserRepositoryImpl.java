package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;
import ua.nure.latysh.quizzes.repositories.UserRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserRepositoryImpl implements UserRepository {

    private static final Logger logger = Logger.getLogger(UserRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

    private User extractUser(ResultSet rs)
            throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setLogin(rs.getString("login"));
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setPassword(rs.getString("password"));
        user.setRegisterDateTime(rs.getTimestamp("register_date"));
        user.setLoginDateTime(rs.getTimestamp("login_date"));
        user.setStatusId(rs.getInt("status_id"));
        user.setRoleId(rs.getInt("role_id"));
        return user;
    }

    @Override
    public User findById(int questionId) {
        User user = new User();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from users where id=?");
            statement.setInt(1, questionId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                user = extractUser(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return user;
    }

    @Override
    public void delete(User user) {
//        if (findById(question.getId()) != null) {
//            try (Connection connection = dbConnector.getConnection()) {
//                PreparedStatement statement = connection.prepareStatement("DELETE FROM questions WHERE id=?");
//                statement.setInt(1, question.getId());
//                statement.executeUpdate();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//        }
    }

    @Override
    public boolean save(User user) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO users (login, password, first_name, last_name, register_date, status_id, role_id) VALUES (?,?,?,?,?,?,?)");
            statement.setString(1, user.getLogin());
            statement.setString(2, user.getPassword());
            statement.setString(3, user.getFirstName());
            statement.setString(4, user.getLastName());
            statement.setTimestamp(5, new Timestamp(user.getRegisterDateTime().getTime()));
            statement.setInt(6, user.getStatusId());
            statement.setInt(7, user.getRoleId());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(User user) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("UPDATE users SET status_id=? WHERE id=?");
            statement.setInt(1, user.getStatusId());
            statement.setInt(2, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    public void updateLoginDate(User user) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("UPDATE users SET login_date=? WHERE id=?");
            statement.setTimestamp(1, new Timestamp(user.getLoginDateTime().getTime()));
            statement.setInt(2, user.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from users where role_id<>1");
            while (rs.next()) {
                users.add(extractUser(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return users;
    }

    public User findByLoginAndPassword(String login, String password) {
        User user = new User();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from users where login=? AND password=?");
            statement.setString(1, login);
            statement.setString(2, password);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                user = extractUser(resultSet);
            } else {
                user = null;
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return user;
    }

    @Override
    public User findByLogin(String login) {
        User user = new User();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from users where login=?");
            statement.setString(1, login);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                user = extractUser(resultSet);
            } else {
                user = null;
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return user;
    }
}
