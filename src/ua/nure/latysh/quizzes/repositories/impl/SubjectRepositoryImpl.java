package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.repositories.SubjectRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SubjectRepositoryImpl implements SubjectRepository {

    private static final Logger logger = Logger.getLogger(SubjectRepositoryImpl.class);
    private final DbConnector dbConnector;

    public SubjectRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    SubjectRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    private Subject extractSubject(ResultSet rs)
            throws SQLException {
        Subject subject = new Subject();
        subject.setId(rs.getInt("id"));
        subject.setName(rs.getString("name"));
        return subject;
    }

    @Override
    public Subject findByName(String subjectName) {
        Subject subject = new Subject();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM subjects WHERE name = ?")) {
            statement.setString(1, subjectName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    subject = extractSubject(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return subject;
    }

    @Override
    public Subject findById(int subjectId) {
        Subject foundSubject = new Subject();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM subjects WHERE id = ?")) {
            statement.setInt(1, subjectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    foundSubject = extractSubject(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return foundSubject;
    }

    @Override
    public void delete(Subject subject) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM subjects WHERE id = ?")) {
            statement.setInt(1, subject.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public boolean save(Subject subject) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO subjects (name) VALUES (?)")) {
            statement.setString(1, subject.getName());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Subject subject) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE subjects SET name = ? WHERE id = ?")) {
            statement.setString(1, subject.getName());
            statement.setInt(2, subject.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
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
        } catch (SQLException e) {
            logger.error(e);
        }
        return subjects;
    }

}
