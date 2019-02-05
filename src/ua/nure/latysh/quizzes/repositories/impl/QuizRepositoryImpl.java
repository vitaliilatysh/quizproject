package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.repositories.QuizRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuizRepositoryImpl implements QuizRepository {

    private final static Logger logger = Logger.getLogger(QuizRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

    private Quiz extractQuiz(ResultSet rs)
            throws SQLException {
        Quiz quiz = new Quiz();
        quiz.setId(rs.getInt("id"));
        quiz.setName(rs.getString("name"));
        quiz.setTimeToPass(rs.getInt("time_to_pass"));
        quiz.setLevelId(rs.getInt("level_id"));
        quiz.setSubjectId(rs.getInt("subject_id"));
        return quiz;
    }

    @Override
    public Quiz findByName(String quizName) {
        Quiz quiz = new Quiz();
        try (Connection connection = DbConnector.getInstance().getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from quizzes where name=?");
            statement.setString(1, quizName);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                quiz = extractQuiz(resultSet);
                logger.info("Found " + quizName);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return quiz;
    }

    @Override
    public List<Quiz> findBySubjectId(int subjectId) {
        List<Quiz> quizzes = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = DbConnector.getInstance().getConnection();
            con.setAutoCommit(false);
            stmt = con.prepareStatement("select * from quizzes where subject_id=?");
            stmt.setInt(1, subjectId);
            rs = stmt.executeQuery();
            while (rs.next()) {
                quizzes.add(extractQuiz(rs));
            }
            con.commit();
            logger.info("Found " + quizzes.size() + " by subjectId:" + subjectId);
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return quizzes;
    }

    @Override
    public List<Quiz> findBySubjectName(String subjectName) {
        List<Quiz> quizzes = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = DbConnector.getInstance().getConnection();
            con.setAutoCommit(false);
            stmt = con.prepareStatement("select quizzes.id,quizzes.name, time_to_pass, level_id, subject_id from quizzes inner join subjects s on quizzes.subject_id = s.id where s.name LIKE ?");
            stmt.setString(1, "%" + subjectName + "%");
            rs = stmt.executeQuery();
            while (rs.next()) {
                quizzes.add(extractQuiz(rs));
            }
            con.commit();
            logger.info("Found " + quizzes.size() + " by subject name:" + subjectName);
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return quizzes;
    }

    @Override
    public Quiz findById(int quizId) {
        Quiz quiz = new Quiz();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from quizzes where id=?");
            statement.setInt(1, quizId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                quiz = extractQuiz(resultSet);
                logger.info("Found " + quiz.getName() + " by quiz id:" + quizId);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return quiz;
    }

    @Override
    public void delete(Quiz quiz) {
        if (findById(quiz.getId()) != null) {
            try (Connection connection = dbConnector.getConnection()) {
                PreparedStatement statement = connection.prepareStatement("DELETE FROM quizzes WHERE id=?");
                statement.setInt(1, quiz.getId());
                statement.executeUpdate();
                logger.info("Deleted " + quiz.getName());
            } catch (SQLException e) {
                logger.error(e);
            }
        }
    }

    @Override
    public boolean save(Quiz quiz) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO quizzes (name, time_to_pass, level_id, subject_id) VALUES (?, ?, ?, ?)");
            statement.setString(1, quiz.getName());
            statement.setInt(2, quiz.getTimeToPass());
            statement.setInt(3, quiz.getLevelId());
            statement.setInt(4, quiz.getSubjectId());
            statement.executeUpdate();
            logger.info("Saved " + quiz.getName());
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Quiz quiz) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("UPDATE quizzes SET name=?, time_to_pass=?, level_id=?, subject_id=? WHERE id=?");
            statement.setString(1, quiz.getName());
            statement.setInt(2, quiz.getTimeToPass());
            statement.setInt(3, quiz.getLevelId());
            statement.setInt(4, quiz.getSubjectId());
            statement.setInt(5, quiz.getId());
            statement.executeUpdate();
            logger.info("Updated " + quiz.getName());
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public List<Quiz> findAll() {
        List<Quiz> quizzes = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from quizzes");
            while (rs.next()) {
                quizzes.add(extractQuiz(rs));
            }
            con.commit();
            logger.info("Found " + quizzes.size() + " quizzes");
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return quizzes;
    }

}
