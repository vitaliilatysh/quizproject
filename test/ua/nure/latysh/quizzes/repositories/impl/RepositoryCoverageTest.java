package ua.nure.latysh.quizzes.repositories.impl;

import org.junit.Test;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.Result;
import ua.nure.latysh.quizzes.entities.Role;
import ua.nure.latysh.quizzes.entities.Status;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.entities.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class RepositoryCoverageTest {

    @Test
    public void answerRepositoryCoversSuccessfulAndFailedJdbcCalls() throws Exception {
        Fixture ok = new Fixture();
        AnswerRepositoryImpl repository = new AnswerRepositoryImpl(ok.dbConnector);
        Answer answer = answer();
        ok.oneRow();
        assertNotNull(repository.findById(1));
        repository.delete(answer);
        assertTrue(repository.save(answer));
        repository.update(answer);
        ok.oneRow();
        assertEquals(1, repository.findAll().size());
        ok.oneRow();
        assertEquals(1, repository.findAllByQuestionId(2).size());

        Fixture failed = new Fixture();
        AnswerRepositoryImpl failing = new AnswerRepositoryImpl(failed.dbConnector);
        failed.failPreparedStatements();
        assertNotNull(failing.findById(1));
        assertFalse(failing.save(answer));
        failing.update(answer);
        assertTrue(failing.findAllByQuestionId(2).isEmpty());
        failed.failStatements();
        assertTrue(failing.findAll().isEmpty());
    }

    @Test
    public void attemptRepositoryCoversSuccessfulAndFailedJdbcCalls() throws Exception {
        Fixture ok = new Fixture();
        AttemptRepositoryImpl repository = new AttemptRepositoryImpl(ok.dbConnector);
        Attempt attempt = attempt();
        ok.oneRow();
        assertNotNull(repository.findById(1));
        repository.delete(attempt);
        assertTrue(repository.save(attempt));
        repository.update(attempt);
        ok.oneRow();
        assertEquals(1, repository.findAll().size());
        ok.oneRow();
        assertEquals(1, repository.findAllByUserId(3).size());
        ok.oneRow();
        assertNotNull(repository.findLastByUserId(3));
        ok.oneRow();
        assertEquals(1, repository.findAllBetweenFinishDates("from", "to").size());

        Fixture failed = new Fixture();
        AttemptRepositoryImpl failing = new AttemptRepositoryImpl(failed.dbConnector);
        failed.failPreparedStatements();
        assertNotNull(failing.findById(1));
        assertFalse(failing.save(attempt));
        failing.update(attempt);
        assertTrue(failing.findAllByUserId(3).isEmpty());
        assertNotNull(failing.findLastByUserId(3));
        assertTrue(failing.findAllBetweenFinishDates("from", "to").isEmpty());
        failed.failStatements();
        assertTrue(failing.findAll().isEmpty());
    }

    @Test
    public void levelRepositoryCoversSuccessfulAndFailedJdbcCalls() throws Exception {
        Fixture ok = new Fixture();
        LevelRepositoryImpl repository = new LevelRepositoryImpl(ok.dbConnector);
        Level level = level();
        ok.oneRow();
        assertNotNull(repository.findById(1));
        ok.oneRow();
        assertNotNull(repository.findByName("hard"));
        repository.delete(level);
        assertTrue(repository.save(level));
        repository.update(level);
        ok.oneRow();
        assertEquals(1, repository.findAll().size());

        Fixture failed = new Fixture();
        LevelRepositoryImpl failing = new LevelRepositoryImpl(failed.dbConnector);
        failed.failPreparedStatements();
        assertNotNull(failing.findById(1));
        assertNotNull(failing.findByName("hard"));
        failed.failStatements();
        assertTrue(failing.findAll().isEmpty());
    }

    @Test
    public void questionRepositoryCoversSuccessfulAndFailedJdbcCalls() throws Exception {
        Fixture ok = new Fixture();
        QuestionRepositoryImpl repository = new QuestionRepositoryImpl(ok.dbConnector);
        Question question = question();
        ok.oneRow();
        assertNotNull(repository.findById(1));
        ok.oneRow();
        repository.delete(question);
        ok.oneRow();
        assertNotNull(repository.saveQuestion(question));
        assertFalse(repository.save(question));
        repository.update(question);
        ok.oneRow();
        assertEquals(1, repository.findAll().size());
        ok.oneRow();
        assertEquals(1, repository.findAllByQuizId(2).size());
        ok.oneRow();
        assertNotNull(repository.findByName("Question"));

        Fixture failed = new Fixture();
        QuestionRepositoryImpl failing = new QuestionRepositoryImpl(failed.dbConnector);
        failed.failPreparedStatements();
        assertNotNull(failing.findById(1));
        failing.delete(question);
        assertNotNull(failing.saveQuestion(question));
        failing.update(question);
        assertTrue(failing.findAllByQuizId(2).isEmpty());
        assertNotNull(failing.findByName("Question"));
        failed.failStatements();
        assertTrue(failing.findAll().isEmpty());
    }

    @Test
    public void quizRepositoryCoversSuccessfulEmptyAndFailedJdbcCalls() throws Exception {
        Fixture ok = new Fixture();
        QuizRepositoryImpl repository = new QuizRepositoryImpl(ok.dbConnector);
        Quiz quiz = quiz();
        ok.oneRow();
        assertNotNull(repository.findByName("Quiz"));
        ok.empty();
        assertNotNull(repository.findByName("missing"));
        ok.oneRow();
        assertEquals(1, repository.findBySubjectId(2).size());
        ok.oneRow();
        assertEquals(1, repository.findBySubjectName("Java").size());
        ok.oneRow();
        assertNotNull(repository.findById(1));
        ok.empty();
        assertNotNull(repository.findById(404));
        repository.delete(quiz);
        assertTrue(repository.save(quiz));
        repository.update(quiz);
        ok.oneRow();
        assertEquals(1, repository.findAll().size());

        Fixture failed = new Fixture();
        QuizRepositoryImpl failing = new QuizRepositoryImpl(failed.dbConnector);
        failed.failPreparedStatements();
        assertNotNull(failing.findByName("Quiz"));
        assertTrue(failing.findBySubjectId(2).isEmpty());
        assertTrue(failing.findBySubjectName("Java").isEmpty());
        assertNotNull(failing.findById(1));
        failing.delete(quiz);
        assertFalse(failing.save(quiz));
        failing.update(quiz);
        failed.failStatements();
        assertTrue(failing.findAll().isEmpty());
    }

    @Test
    public void resultRepositoryCoversSuccessfulAndFailedJdbcCalls() throws Exception {
        Fixture ok = new Fixture();
        ResultRepositoryImpl repository = new ResultRepositoryImpl(ok.dbConnector);
        Result result = result();
        assertNotNull(repository.findById(1));
        repository.delete(result);
        assertTrue(repository.save(result));
        repository.update(result);
        ok.oneRow();
        assertEquals(1, repository.findAll().size());
        ok.oneRow();
        assertEquals(1, repository.findByAttemptId(2).size());
        ok.oneRow();
        assertEquals(1, repository.findAllByUserId(3).size());

        Fixture failed = new Fixture();
        ResultRepositoryImpl failing = new ResultRepositoryImpl(failed.dbConnector);
        failed.failPreparedStatements();
        assertFalse(failing.save(result));
        assertTrue(failing.findByAttemptId(2).isEmpty());
        assertTrue(failing.findAllByUserId(3).isEmpty());
        failed.failStatements();
        assertTrue(failing.findAll().isEmpty());
    }

    @Test
    public void roleAndStatusRepositoriesCoverSuccessfulAndFailedJdbcCalls() throws Exception {
        Fixture roleOk = new Fixture();
        RoleRepositoryImpl roles = new RoleRepositoryImpl(roleOk.dbConnector);
        Role role = role();
        roleOk.oneRow();
        assertNotNull(roles.findById(1));
        roles.delete(role);
        assertTrue(roles.save(role));
        roles.update(role);
        roleOk.oneRow();
        assertEquals(1, roles.findAll().size());

        Fixture roleFailed = new Fixture();
        RoleRepositoryImpl failingRoles = new RoleRepositoryImpl(roleFailed.dbConnector);
        roleFailed.failPreparedStatements();
        assertNotNull(failingRoles.findById(1));
        assertFalse(failingRoles.save(role));
        roleFailed.failStatements();
        assertTrue(failingRoles.findAll().isEmpty());

        Fixture statusOk = new Fixture();
        StatusRepositoryImpl statuses = new StatusRepositoryImpl(statusOk.dbConnector);
        Status status = status();
        statusOk.oneRow();
        assertNotNull(statuses.findById(1));
        statuses.delete(status);
        assertTrue(statuses.save(status));
        statuses.update(status);
        statusOk.oneRow();
        assertEquals(1, statuses.findAll().size());

        Fixture statusFailed = new Fixture();
        StatusRepositoryImpl failingStatuses = new StatusRepositoryImpl(statusFailed.dbConnector);
        statusFailed.failPreparedStatements();
        assertNotNull(failingStatuses.findById(1));
        assertFalse(failingStatuses.save(status));
        statusFailed.failStatements();
        assertTrue(failingStatuses.findAll().isEmpty());
    }

    @Test
    public void subjectRepositoryCoversSuccessfulAndFailedJdbcCalls() throws Exception {
        Fixture ok = new Fixture();
        SubjectRepositoryImpl repository = new SubjectRepositoryImpl(ok.dbConnector);
        Subject subject = subject();
        ok.oneRow();
        assertNotNull(repository.findByName("Java"));
        ok.oneRow();
        assertNotNull(repository.findById(1));
        ok.oneRow();
        repository.delete(subject);
        assertTrue(repository.save(subject));
        repository.update(subject);
        ok.oneRow();
        assertEquals(1, repository.findAll().size());

        Fixture failed = new Fixture();
        SubjectRepositoryImpl failing = new SubjectRepositoryImpl(failed.dbConnector);
        failed.failPreparedStatements();
        assertNotNull(failing.findByName("Java"));
        assertNotNull(failing.findById(1));
        failing.delete(subject);
        assertFalse(failing.save(subject));
        failing.update(subject);
        failed.failStatements();
        assertTrue(failing.findAll().isEmpty());
    }

    @Test
    public void userRepositoryCoversSuccessfulEmptyAndFailedJdbcCalls() throws Exception {
        Fixture ok = new Fixture();
        UserRepositoryImpl repository = new UserRepositoryImpl(ok.dbConnector);
        User user = user();
        ok.oneRow();
        assertNotNull(repository.findById(1));
        repository.delete(user);
        assertTrue(repository.save(user));
        repository.update(user);
        repository.updateLoginDate(user);
        repository.updatePassword(user);
        ok.oneRow();
        assertEquals(1, repository.findAll().size());
        ok.oneRow();
        assertNotNull(repository.findByLogin("user"));
        ok.empty();
        assertEquals(null, repository.findByLogin("missing"));

        Fixture failed = new Fixture();
        UserRepositoryImpl failing = new UserRepositoryImpl(failed.dbConnector);
        failed.failPreparedStatements();
        assertNotNull(failing.findById(1));
        assertFalse(failing.save(user));
        failing.update(user);
        failing.updateLoginDate(user);
        failing.updatePassword(user);
        assertNotNull(failing.findByLogin("user"));
        failed.failStatements();
        assertTrue(failing.findAll().isEmpty());
    }

    private static Answer answer() {
        Answer answer = new Answer();
        answer.setId(1);
        answer.setAnswer("A");
        answer.setCorrect(true);
        answer.setQuestionId(2);
        return answer;
    }

    private static Attempt attempt() {
        Attempt attempt = new Attempt();
        attempt.setId(1);
        attempt.setScore(80);
        attempt.setStartTime(new Date());
        attempt.setExpiresAt(new Date(System.currentTimeMillis() + 60_000));
        attempt.setEndTime(new Date());
        attempt.setQuizId(2);
        attempt.setUserId(3);
        return attempt;
    }

    private static Level level() {
        Level level = new Level();
        level.setId(1);
        level.setLevelName("hard");
        return level;
    }

    private static Question question() {
        Question question = new Question();
        question.setId(1);
        question.setQuestion("Question");
        question.setQuizId(2);
        return question;
    }

    private static Quiz quiz() {
        Quiz quiz = new Quiz();
        quiz.setId(1);
        quiz.setName("Quiz");
        quiz.setTimeToPass(15);
        quiz.setLevelId(1);
        quiz.setSubjectId(2);
        return quiz;
    }

    private static Result result() {
        Result result = new Result();
        result.setId(1);
        result.setAnswerId(1);
        result.setAttemptId(2);
        return result;
    }

    private static Role role() {
        Role role = new Role();
        role.setId(1);
        role.setRole("user");
        return role;
    }

    private static Status status() {
        Status status = new Status();
        status.setId(1);
        status.setStatus("active");
        return status;
    }

    private static Subject subject() {
        Subject subject = new Subject();
        subject.setId(1);
        subject.setName("Java");
        return subject;
    }

    private static User user() {
        User user = new User();
        user.setId(1);
        user.setLogin("user");
        user.setPassword("secret");
        user.setFirstName("First");
        user.setLastName("Last");
        user.setRegisterDateTime(new Date());
        user.setLoginDateTime(new Date());
        user.setStatusId(1);
        user.setRoleId(2);
        return user;
    }

    private static final class Fixture {
        private final DbConnector dbConnector = mock(DbConnector.class);
        private final Connection connection = mock(Connection.class);
        private final PreparedStatement preparedStatement = mock(PreparedStatement.class);
        private final Statement statement = mock(Statement.class);
        private final ResultSet resultSet = mock(ResultSet.class);
        private final ResultSet generatedKeys = mock(ResultSet.class);

        private Fixture() throws SQLException {
            when(dbConnector.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(connection.prepareStatement(anyString(), anyInt())).thenReturn(preparedStatement);
            when(connection.createStatement()).thenReturn(statement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(preparedStatement.getGeneratedKeys()).thenReturn(generatedKeys);
            when(statement.executeQuery(anyString())).thenReturn(resultSet);
            when(resultSet.getTimestamp(anyString())).thenReturn(new Timestamp(1_700_000_000_000L));
            when(generatedKeys.next()).thenReturn(true);
            when(generatedKeys.getInt(1)).thenReturn(42);
        }

        private void oneRow() throws SQLException {
            reset(resultSet);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getTimestamp(anyString())).thenReturn(new Timestamp(1_700_000_000_000L));
        }

        private void empty() throws SQLException {
            reset(resultSet);
            when(resultSet.next()).thenReturn(false);
        }

        private void failPreparedStatements() throws SQLException {
            when(connection.prepareStatement(anyString())).thenThrow(new SQLException("failed"));
            when(connection.prepareStatement(anyString(), anyInt())).thenThrow(new SQLException("failed"));
        }

        private void failStatements() throws SQLException {
            when(connection.createStatement()).thenThrow(new SQLException("failed"));
        }
    }
}
