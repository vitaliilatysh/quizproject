package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AnswerRepositoryImpl implements AnswerRepository {

    private final static Logger logger = Logger.getLogger(AnswerRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

    @Override
    public Answer findById(int answerId) {
        Answer answer = new Answer();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from answers where id=?");
            statement.setInt(1, answerId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                answer = extractAnswer(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return answer;
    }

    @Override
    public void delete(Answer answer) {
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
    public boolean save(Answer answer) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO answers (answer, correct, question_id) VALUES (?, ?, ?)");
            statement.setString(1, answer.getAnswer());
            statement.setBoolean(2, answer.isCorrect());
            statement.setInt(3, answer.getQuestionId());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error(e);
            return false;
        }
    }

    @Override
    public void update(Answer answer) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("UPDATE answers SET answer=?, correct=? WHERE id=?");
            statement.setString(1, answer.getAnswer());
            statement.setBoolean(2, answer.isCorrect());
            statement.setInt(3, answer.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public List<Answer> findAll() {
        List<Answer> answers = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from answers");
            while (rs.next()) {
                answers.add(extractAnswer(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return answers;
    }

    private Answer extractAnswer(ResultSet rs)
            throws SQLException {
        Answer answer = new Answer();
        answer.setId(rs.getInt("id"));
        answer.setAnswer(rs.getString("answer"));
        answer.setCorrect(rs.getBoolean("correct"));
        answer.setQuestionId(rs.getInt("question_id"));
        return answer;
    }

    @Override
    public List<Answer> findAllByQuestionId(int questionId) {
        List<Answer> answers = new ArrayList<>();
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            pstmt = con.prepareStatement("SELECT * from answers inner join questions on questions.id = answers.question_id where question_id=?");
            pstmt.setLong(1, questionId);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                answers.add(extractAnswer(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, pstmt, rs);
        }
        return answers;
    }

}
