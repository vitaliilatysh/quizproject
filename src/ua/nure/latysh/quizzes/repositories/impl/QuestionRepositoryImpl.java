package ua.nure.latysh.quizzes.repositories.impl;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.repositories.QuestionRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class QuestionRepositoryImpl implements QuestionRepository {

    private static final Logger logger = Logger.getLogger(QuestionRepositoryImpl.class);
    private final DbConnector dbConnector;

    public QuestionRepositoryImpl() {
        this(DbConnector.getInstance());
    }

    QuestionRepositoryImpl(DbConnector dbConnector) {
        this.dbConnector = dbConnector;
    }

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
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM questions WHERE id = ?")) {
            statement.setInt(1, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    question = extractQuestion(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return question;
    }

    @Override
    public void delete(Question question) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM questions WHERE id = ?")) {
            statement.setInt(1, question.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public Question saveQuestion(Question question) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO questions (question, quiz_id) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, question.getQuestion());
            statement.setInt(2, question.getQuizId());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    Question savedQuestion = new Question();
                    savedQuestion.setId(generatedKeys.getInt(1));
                    savedQuestion.setQuestion(question.getQuestion());
                    savedQuestion.setQuizId(question.getQuizId());
                    return savedQuestion;
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return new Question();
    }

    @Override
    public boolean save(Question element) {
        return saveQuestion(element).getId() > 0;
    }

    @Override
    public void update(Question question) {
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE questions SET question = ?, quiz_id = ? WHERE id = ?")) {
            statement.setString(1, question.getQuestion());
            statement.setInt(2, question.getQuizId());
            statement.setInt(3, question.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error(e);
        }
    }

    @Override
    public List<Question> findAll() {
        List<Question> questions = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM questions")) {
            while (resultSet.next()) {
                questions.add(extractQuestion(resultSet));
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return questions;
    }

    @Override
    public List<Question> findAllByQuizId(int quizId) {
        List<Question> questions = new ArrayList<>();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM questions WHERE quiz_id = ?")) {
            statement.setInt(1, quizId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    questions.add(extractQuestion(resultSet));
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return questions;
    }

    @Override
    public Question findByName(String questionName) {
        Question question = new Question();
        try (Connection connection = dbConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM questions WHERE question = ?")) {
            statement.setString(1, questionName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    question = extractQuestion(resultSet);
                }
            }
        } catch (SQLException e) {
            logger.error(e);
        }
        return question;
    }
}
