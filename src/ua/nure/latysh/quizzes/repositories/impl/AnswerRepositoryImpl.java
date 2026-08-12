package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AnswerRepositoryImpl implements AnswerRepository {

    private static final Logger logger = Logger.getLogger(AnswerRepositoryImpl.class);
    private final DbConnector dbConnector;

    public AnswerRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    AnswerRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Answer findById(int answerId) {
        Answer answer = new Answer();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM answers WHERE id = ?")) {
            statement.setInt(1, answerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    answer = extractAnswer(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return answer;
    }

    @Override
    public void delete(Answer answer) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM answers WHERE id = ?")) {
            statement.setInt(1, answer.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public boolean save(Answer answer) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO answers (answer, correct, question_id) VALUES (?, ?, ?)")) {
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
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE answers SET answer = ?, correct = ? WHERE id = ?")) {
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
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM answers")) {
            while (resultSet.next()) {
                answers.add(extractAnswer(resultSet));
            }
        } catch (SQLException e) {
            logger.error(e);
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
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM answers WHERE question_id = ?")) {
            statement.setInt(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    answers.add(extractAnswer(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return answers;
    }

}
