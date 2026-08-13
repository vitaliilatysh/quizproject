package ua.nure.latysh.quizzes.repositories.impl;

import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;
import ua.nure.latysh.quizzes.repositories.SubjectRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubjectRepositoryImpl implements SubjectRepository {
    private final DbConnector dbConnector;

    public SubjectRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    SubjectRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Optional<Subject> findByName(String subjectName) {
        return findOne("SELECT * FROM subjects WHERE name = ?", statement -> statement.setString(1, subjectName));
    }

    @Override
    public Optional<Subject> findById(int subjectId) {
        return findOne("SELECT * FROM subjects WHERE id = ?", statement -> statement.setInt(1, subjectId));
    }

    @Override
    public void delete(Subject subject) {
        execute("DELETE FROM subjects WHERE id = ?", statement -> statement.setInt(1, subject.getId()),
                "delete subject");
    }

    @Override
    public boolean save(Subject subject) {
        execute("INSERT INTO subjects (name) VALUES (?)", statement -> statement.setString(1, subject.getName()),
                "save subject");
        return true;
    }

    @Override
    public void update(Subject subject) {
        execute("UPDATE subjects SET name = ? WHERE id = ?", statement -> {
            statement.setString(1, subject.getName());
            statement.setInt(2, subject.getId());
        }, "update subject");
    }

    @Override
    public List<Subject> findAll() {
        List<Subject> subjects = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM subjects")) {
            while (resultSet.next()) {
                subjects.add(extractSubject(resultSet));
            }
            return subjects;
        } catch (SQLException exception) {
            throw failure("list subjects", exception);
        }
    }

    private Optional<Subject> findOne(String sql, StatementConfigurer configurer) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            configurer.configure(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(extractSubject(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("find subject", exception);
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

    private Subject extractSubject(ResultSet resultSet) throws SQLException {
        Subject subject = new Subject();
        subject.setId(resultSet.getInt("id"));
        subject.setName(resultSet.getString("name"));
        return subject;
    }

    private RepositoryException failure(String operation, SQLException exception) {
        return new RepositoryException("Could not " + operation, exception);
    }

    @FunctionalInterface
    private interface StatementConfigurer {
        void configure(PreparedStatement statement) throws SQLException;
    }
}
