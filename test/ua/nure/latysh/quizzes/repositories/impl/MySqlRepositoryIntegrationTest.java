package ua.nure.latysh.quizzes.repositories.impl;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.flywaydb.core.Flyway;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.mysql.MySQLContainer;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Subject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MySqlRepositoryIntegrationTest {
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.36")
            .withDatabaseName("tests_db")
            .withUsername("quiz")
            .withPassword("quiz-password");

    private static MysqlDataSource dataSource;

    @BeforeClass
    public static void migrateDatabase() {
        MYSQL.start();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());
    }

    @AfterClass
    public static void stopDatabase() {
        MYSQL.stop();
    }

    @Test
    public void migrationsProduceSecureSchemaAndRepositoryCrudWorks() throws Exception {
        SubjectRepositoryImpl repository = new SubjectRepositoryImpl(new DbConnector(dataSource));
        Subject subject = new Subject();
        subject.setName("Integration Testing");

        assertTrue(repository.save(subject));
        Optional<Subject> saved = repository.findByName("Integration Testing");
        assertTrue(saved.isPresent());
        assertEquals("Integration Testing", repository.findById(saved.get().getId()).orElseThrow().getName());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.columns "
                             + "WHERE table_schema = DATABASE() AND table_name = 'attempts' "
                             + "AND column_name IN ('start_time', 'expires_at', 'completed')")) {
            columns.next();
            assertEquals(3, columns.getInt(1));
        }

        repository.delete(saved.get());
        assertTrue(repository.findByName("Integration Testing").isEmpty());
    }

    @Test
    public void questionAggregateCreateAndUpdateAreAtomic() {
        QuestionRepositoryImpl repository = new QuestionRepositoryImpl(new DbConnector(dataSource));
        Question question = new Question();
        question.setQuestion("Atomic question");
        question.setQuizId(1);

        Question saved = repository.createWithAnswers(question, answers("Initial"));

        List<Answer> savedAnswers = new AnswerRepositoryImpl(new DbConnector(dataSource))
                .findAllByQuestionId(saved.getId());
        assertEquals(4, savedAnswers.size());
        saved.setQuestion("Updated question");
        for (int index = 0; index < savedAnswers.size(); index++) {
            savedAnswers.get(index).setAnswer("Updated " + index);
            savedAnswers.get(index).setCorrect(index == 1);
        }
        repository.updateWithAnswers(saved, savedAnswers);

        assertEquals("Updated question", repository.findById(saved.getId()).orElseThrow().getQuestion());
        List<Answer> updatedAnswers = new AnswerRepositoryImpl(new DbConnector(dataSource))
                .findAllByQuestionId(saved.getId());
        assertEquals("Updated 0", updatedAnswers.get(0).getAnswer());
        assertTrue(updatedAnswers.get(1).isCorrect());
        repository.delete(saved);
    }

    private static List<Answer> answers(String prefix) {
        return java.util.stream.IntStream.range(0, 4).mapToObj(index -> {
            Answer answer = new Answer();
            answer.setAnswer(prefix + " " + index);
            answer.setCorrect(index == 0);
            return answer;
        }).toList();
    }
}

