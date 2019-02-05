package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Result;
import ua.nure.latysh.quizzes.repositories.ResultRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ResultRepositoryImpl implements ResultRepository {

    private final static Logger logger = Logger.getLogger(ResultRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

    @Override
    public Result findById(int answerId) {
//        Subject foundSubject = new Subject();
//        try (Connection connection = dbConnector.getConnection()) {
//            PreparedStatement statement = connection.prepareStatement("select * from subjects where id=?");
//            statement.setInt(1, subjectId);
//            ResultSet resultSet = statement.executeQuery();
//
//            if (resultSet.next()) {
//                foundSubject.setId(resultSet.getInt("id"));
//                foundSubject.setName(resultSet.getString("name"));
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//        return foundSubject;
        return new Result();
    }

    @Override
    public void delete(Result answer) {
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
    public boolean save(Result result) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO results (answer_id, attempt_id) VALUES (?, ?)");
            statement.setInt(1, result.getAnswerId());
            statement.setInt(2, result.getAttemptId());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Result answer) {
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
    public List<Result> findAll() {
        List<Result> results = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from results");
            while (rs.next()) {
                results.add(extractResult(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return results;
    }

    private Result extractResult(ResultSet rs)
            throws SQLException {
        Result result = new Result();
        result.setId(rs.getInt("id"));
        result.setAnswerId(rs.getInt("answer_id"));
        result.setAttemptId(rs.getInt("attempt_id"));
        return result;
    }

    @Override
    public List<Result> findByAttemptId(int attemptId) {
        List<Result> results = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from results where attempt_id=?");
            statement.setInt(1, attemptId);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                results.add(extractResult(resultSet));
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return results;
    }

    @Override
    public List<Result> findAllByUserId(int userId) {
        List<Result> results = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from results inner join attempts on results.attempt_id = attempts.id where user_id=?");
            statement.setInt(1, userId);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                results.add(extractResult(resultSet));
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return results;
    }
}
