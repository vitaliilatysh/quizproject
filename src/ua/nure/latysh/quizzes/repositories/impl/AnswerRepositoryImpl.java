package ua.nure.latysh.quizzes.repositories.impl;

import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;
import ua.nure.latysh.quizzes.repositories.AnswerRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AnswerRepositoryImpl implements AnswerRepository {
    private final DbConnector dbConnector;

    public AnswerRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    AnswerRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

    @Override
    public Optional<Answer> findById(int answerId) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM answers WHERE id = ?")) {
            statement.setInt(1, answerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(extractAnswer(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("find answer by id", exception);
        }
    }

    @Override
    public void delete(Answer answer) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM answers WHERE id = ?")) {
            statement.setInt(1, answer.getId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw failure("delete answer", exception);
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
        } catch (SQLException exception) {
            throw failure("save answer", exception);
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
        } catch (SQLException exception) {
            throw failure("update answer", exception);
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
            return answers;
        } catch (SQLException exception) {
            throw failure("list answers", exception);
        }
    }

    @Override
    public List<Answer> findAllByQuestionId(int questionId) {
        List<Answer> answers = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM answers WHERE question_id = ? ORDER BY id")) {
            statement.setInt(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    answers.add(extractAnswer(resultSet));
                }
            }
            return answers;
        } catch (SQLException exception) {
            throw failure("list answers by question", exception);
        }
    }

    private Answer extractAnswer(ResultSet resultSet) throws SQLException {
        Answer answer = new Answer();
        answer.setId(resultSet.getInt("id"));
        answer.setAnswer(resultSet.getString("answer"));
        answer.setCorrect(resultSet.getBoolean("correct"));
        answer.setQuestionId(resultSet.getInt("question_id"));
        return answer;
    }

    private RepositoryException failure(String operation, SQLException exception) {
        return new RepositoryException("Could not " + operation, exception);
    }
}

