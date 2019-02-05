package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuestionRepositoryImpl implements QuestionRepository {

    private final static Logger logger = Logger.getLogger(QuestionRepositoryImpl.class);
    private DbConnector dbConnector = DbConnector.getInstance();

    private Question extractQuestion(ResultSet rs)
            throws SQLException {
        Question question = new Question();
        question.setId(rs.getInt("id"));
        question.setQuestion(rs.getString("question"));
        question.setQuizId(rs.getInt("quiz_id"));
        return question;
    }

    @Override
    public Question findById(int questionId) {
        Question question = new Question();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from questions where id=?");
            statement.setInt(1, questionId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                question = extractQuestion(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return question;
    }

    @Override
    public void delete(Question question) {
        if (findById(question.getId()) != null) {
            try (Connection connection = dbConnector.getConnection()) {
                PreparedStatement statement = connection.prepareStatement("DELETE FROM questions WHERE id=?");
                statement.setInt(1, question.getId());
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.error(e);
            }
        }
    }

    @Override
    public Question saveQuestion(Question question) {
        Question newQuestion = new Question();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            connection = dbConnector.getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement("INSERT INTO questions (question, quiz_id) VALUES (?, ?)");
            statement.setString(1, question.getQuestion());
            statement.setInt(2, question.getQuizId());
            statement.executeUpdate();
            statement = connection.prepareStatement("SELECT * FROM questions WHERE id = (SELECT MAX(id) FROM questions WHERE quiz_id=?)");
            statement.setInt(1, question.getQuizId());
            rs = statement.executeQuery();
            if (rs.next()) {
                newQuestion = extractQuestion(rs);
            }
            connection.commit();
        } catch (SQLException e) {
            dbConnector.rollback(connection);
            logger.error(e);
        } finally {
            dbConnector.close(connection, statement, rs);
        }
        return newQuestion;
    }

    @Override
    public boolean save(Question element) {
        return false;
    }

    @Override
    public void update(Question question) {
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("UPDATE questions SET question=? WHERE id=?");
            statement.setString(1, question.getQuestion());
            statement.setInt(2, question.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public List<Question> findAll() {
        List<Question> questions = new ArrayList<>();
        Statement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = dbConnector.getConnection();
            con.setAutoCommit(false);
            stmt = con.createStatement();
            rs = stmt.executeQuery("SELECT * from questions");
            while (rs.next()) {
                questions.add(extractQuestion(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return questions;
    }

    @Override
    public List<Question> findAllByQuizId(int quizId) {
        List<Question> questions = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = DbConnector.getInstance().getConnection();
            con.setAutoCommit(false);
            stmt = con.prepareStatement("select * from questions where quiz_id=?");
            stmt.setInt(1, quizId);
            rs = stmt.executeQuery();
            while (rs.next()) {
                questions.add(extractQuestion(rs));
            }
            con.commit();
        } catch (SQLException ex) {
            dbConnector.rollback(con);
            logger.error(ex);
        } finally {
            dbConnector.close(con, stmt, rs);
        }
        return questions;
    }

    @Override
    public Question findByName(String questionName) {
        Question question = new Question();
        try (Connection connection = dbConnector.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("select * from questions where question=?");
            statement.setString(1, questionName);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                question = extractQuestion(resultSet);
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return question;
    }
}
