package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.repositories.LevelRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class LevelRepositoryImpl implements LevelRepository {

    private static final Logger logger = Logger.getLogger(LevelRepositoryImpl.class);
    private final DbConnector dbConnector;

    public LevelRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    LevelRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

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
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM levels WHERE id = ?")) {
            statement.setInt(1, levelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    level = extractLevel(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return level;
    }

    @Override
    public Level findByName(String levelName) {
        Level level = new Level();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM levels WHERE level = ?")) {
            statement.setString(1, levelName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    level = extractLevel(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return level;
    }

    @Override
    public void delete(Level level) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM levels WHERE id = ?")) {
            statement.setInt(1, level.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public boolean save(Level level) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO levels (level) VALUES (?)")) {
            statement.setString(1, level.getLevelName());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Level level) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE levels SET level = ? WHERE id = ?")) {
            statement.setString(1, level.getLevelName());
            statement.setInt(2, level.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public List<Level> findAll() {
        List<Level> levels = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM levels")) {
            while (resultSet.next()) {
                levels.add(extractLevel(resultSet));
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return levels;
    }
}
