package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.repositories.SubjectRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SubjectRepositoryImpl implements SubjectRepository {

    private final static Logger logger = Logger.getLogger(SubjectRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

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
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from subjects where name=?");
            statement.setString(1, subjectName);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                subject = extractSubject(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return subject;
    }

    @Override
    public Subject findById(int subjectId) {
        Subject foundSubject = new Subject();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from subjects where id=?");
            statement.setInt(1, subjectId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                foundSubject.setId(resultSet.getInt("id"));
                foundSubject.setName(resultSet.getString("name"));
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return foundSubject;
    }

    @Override
    public void delete(Subject subject) {
        if (findById(subject.getId()) != null) {
            try (Connection connection = dbConnector.getConnection()) {
                PreparedStatement statement = connection.prepareStatement("DELETE FROM subjects WHERE id=?");
                statement.setInt(1, subject.getId());
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.error(e);
            }
        }
    }

    @Override
    public boolean save(Subject subject) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO subjects (name) VALUES (?)");
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
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("UPDATE subjects SET name=? WHERE id=?");
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
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from subjects");
            while (rs.next()) {
                subjects.add(extractSubject(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return subjects;
    }

}
