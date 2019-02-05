package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;
import ua.nure.latysh.quizzes.repositories.AttemptRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttemptRepositoryImpl implements AttemptRepository {

    private final static Logger logger = Logger.getLogger(AttemptRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

    @Override
    public Attempt findById(int attemptId) {
        Attempt attempt = new Attempt();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from attempts where id=?");
            statement.setInt(1, attemptId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                attempt = extractAttempt(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return attempt;
    }

    @Override
    public void delete(Attempt attempt) {
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
    public boolean save(Attempt attempt) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO attempts (score, quiz_id, user_id) VALUES (?,?,?)");
            statement.setInt(1, attempt.getScore());
            statement.setInt(2, attempt.getQuizId());
            statement.setInt(3, attempt.getUserId());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Attempt attempt) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("UPDATE attempts SET score=?, end_time=? WHERE id=?");
            statement.setInt(1, attempt.getScore());
            statement.setTimestamp(2, new Timestamp(attempt.getEndTime().getTime()));
            statement.setInt(3, attempt.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public List<Attempt> findAll() {
        List<Attempt> attempts = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from attempts");
            while (rs.next()) {
                attempts.add(extractAttempt(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return attempts;
    }

    private Attempt extractAttempt(ResultSet rs)
            throws SQLException {
        Attempt attempt = new Attempt();
        attempt.setId(rs.getInt("id"));
        attempt.setScore(rs.getInt("score"));
        attempt.setEndTime(rs.getTimestamp("end_time"));
        attempt.setQuizId(rs.getInt("quiz_id"));
        attempt.setUserId(rs.getInt("user_id"));
        return attempt;
    }

    @Override
    public List<Attempt> findAllByUserId(int userId) {
        List<Attempt> attempts = new ArrayList<>();
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            pstmt = con.prepareStatement("SELECT * from attempts where user_id=?");
            pstmt.setInt(1, userId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                attempts.add(extractAttempt(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, pstmt, rs);
        }
        return attempts;
    }

    @Override
    public Attempt findLastByUserId(int userId) {
        Attempt attempt = new Attempt();
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            pstmt = con.prepareStatement("SELECT * from attempts WHERE user_id=? ORDER BY id DESC LIMIT 1");
            pstmt.setInt(1, userId);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                attempt = extractAttempt(rs);
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, pstmt, rs);
        }
        return attempt;
    }

    @Override
    public List<Attempt> findAllBetweenFinishDates(String startRange, String endRange) {
        List<Attempt> attempts = new ArrayList<>();
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            pstmt = con.prepareStatement("select * from attempts where end_time between ? AND ?");
            pstmt.setString(1, startRange);
            pstmt.setString(2, endRange);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                attempts.add(extractAttempt(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, pstmt, rs);
        }
        return attempts;
    }


}
