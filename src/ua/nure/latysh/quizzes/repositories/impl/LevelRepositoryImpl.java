package ua.nure.latysh.quizzes.repositories.impl;

import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;
import ua.nure.latysh.quizzes.repositories.LevelRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LevelRepositoryImpl implements LevelRepository {
    private final DbConnector dbConnector;

    public LevelRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    LevelRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Optional<Level> findById(int levelId) {
        return findOne("SELECT * FROM levels WHERE id = ?", statement -> statement.setInt(1, levelId));
    }

    @Override
    public Optional<Level> findByName(String levelName) {
        return findOne("SELECT * FROM levels WHERE level = ?", statement -> statement.setString(1, levelName));
    }

    @Override
    public void delete(Level level) {
        execute("DELETE FROM levels WHERE id = ?", statement -> statement.setInt(1, level.getId()), "delete level");
    }

    @Override
    public boolean save(Level level) {
        execute("INSERT INTO levels (level) VALUES (?)", statement -> statement.setString(1, level.getLevelName()),
                "save level");
        return true;
    }

    @Override
    public void update(Level level) {
        execute("UPDATE levels SET level = ? WHERE id = ?", statement -> {
            statement.setString(1, level.getLevelName());
            statement.setInt(2, level.getId());
        }, "update level");
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
            return levels;
        } catch (SQLException exception) {
            throw failure("list levels", exception);
        }
    }

    private Optional<Level> findOne(String sql, StatementConfigurer configurer) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(extractLevel(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("find level", exception);
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

    private Level extractLevel(ResultSet resultSet) throws SQLException {
        Level level = new Level();
        level.setId(resultSet.getInt("id"));
        level.setLevelName(resultSet.getString("level"));
        return level;
    }

    private RepositoryException failure(String operation, SQLException exception) {
        return new RepositoryException("Could not " + operation, exception);
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
