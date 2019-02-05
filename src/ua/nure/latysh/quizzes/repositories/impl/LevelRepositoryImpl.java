package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.repositories.LevelRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LevelRepositoryImpl implements LevelRepository {

    private final static Logger logger = Logger.getLogger(LevelRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

    private Level extractLevel(ResultSet rs)
            throws SQLException {
        Level level = new Level();
        level.setId(rs.getInt("id"));
        level.setLevelName(rs.getString("level"));
        return level;
    }

    @Override
    public Level findById(int levelId) {
        Level level = new Level();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from levels where id=?");
            statement.setInt(1, levelId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                level = extractLevel(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return level;
    }

    @Override
    public Level findByName(String levelName) {
        Level level = new Level();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from levels where level=?");
            statement.setString(1, levelName);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                level = extractLevel(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return level;
    }

    @Override
    public void delete(Level level) {
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
    public boolean save(Level level) {
//        try (Connection connection = dbConnector.getConnection()) {
//            PreparedStatement statement = connection.prepareStatement("INSERT INTO questions (name) VALUES (?)");
//            statement.setString(1, question.getQuestion());
//            statement.executeUpdate();
//            return true;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            return false;
//        }
        return true;
    }

    @Override
    public void update(Level level) {
//        try (Connection connection = dbConnector.getConnection()) {
//            PreparedStatement statement = connection.prepareStatement("UPDATE questions SET name=? WHERE id=?");
//            statement.setString(1, question.getQuestion());
//            statement.setInt(2, question.getId());
//            statement.executeUpdate();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public List<Level> findAll() {
        List<Level> levels = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from levels");
            while (rs.next()) {
                levels.add(extractLevel(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return levels;
    }
}
