package ua.nure.latysh.quizzes.repositories.impl;

import org.junit.Test;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.exceptions.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

public class QuestionAggregateRepositoryTest {

    @Test
    public void createPersistsQuestionAndAnswersInOneTransaction() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.generatedKeys.next()).thenReturn(true);
        when(fixture.generatedKeys.getInt(1)).thenReturn(42);
        Question question = question(0, 7);

        Question saved = fixture.repository.createWithAnswers(question, answers(false));

        assertEquals(42, saved.getId());
        verify(fixture.connection).setAutoCommit(false);
        verify(fixture.answerStatement, times(4)).addBatch();
        verify(fixture.answerStatement).executeBatch();
        verify(fixture.connection).commit();
        verify(fixture.dbConnector, never()).rollback(fixture.connection);
    }

    @Test
    public void createRollsBackWhenGeneratedIdOrAnswerBatchFails() throws Exception {
        Fixture missingKey = new Fixture();
        when(missingKey.generatedKeys.next()).thenReturn(false);
        assertThrows(RepositoryException.class,
                () -> missingKey.repository.createWithAnswers(question(0, 7), answers(false)));
        verify(missingKey.dbConnector).rollback(missingKey.connection);

        Fixture failedBatch = new Fixture();
        when(failedBatch.generatedKeys.next()).thenReturn(true);
        doThrow(new SQLException("failed")).when(failedBatch.answerStatement).executeBatch();
        assertThrows(RepositoryException.class,
                () -> failedBatch.repository.createWithAnswers(question(0, 7), answers(false)));
        verify(failedBatch.dbConnector).rollback(failedBatch.connection);

        Fixture setupFailure = new Fixture();
        doThrow(new SQLException("failed")).when(setupFailure.connection).setAutoCommit(false);
        assertThrows(RepositoryException.class,
                () -> setupFailure.repository.createWithAnswers(question(0, 7), answers(false)));

        Fixture updateSqlFailure = new Fixture();
        doThrow(new SQLException("failed")).when(updateSqlFailure.questionStatement).executeUpdate();
        assertThrows(RepositoryException.class,
                () -> updateSqlFailure.repository.updateWithAnswers(question(9, 7), answers(true)));
        verify(updateSqlFailure.dbConnector).rollback(updateSqlFailure.connection);
    }

    @Test
    public void updateChecksOwnershipAndCommitsAllChanges() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.questionStatement.executeUpdate()).thenReturn(1);
        when(fixture.answerStatement.executeBatch()).thenReturn(new int[]{1, 1, 1, 1});

        fixture.repository.updateWithAnswers(question(9, 7), answers(true));

        verify(fixture.answerStatement).setInt(4, 9);
        verify(fixture.connection).commit();
    }

    @Test
    public void updateRollsBackMissingQuestionMissingAnswerAndRollbackFailure() throws Exception {
        Fixture missingQuestion = new Fixture();
        when(missingQuestion.questionStatement.executeUpdate()).thenReturn(0);
        assertThrows(RepositoryException.class,
                () -> missingQuestion.repository.updateWithAnswers(question(9, 7), answers(true)));
        verify(missingQuestion.dbConnector).rollback(missingQuestion.connection);

        Fixture missingAnswer = new Fixture();
        when(missingAnswer.questionStatement.executeUpdate()).thenReturn(1);
        when(missingAnswer.answerStatement.executeBatch()).thenReturn(new int[]{1, 1, 0, 1});
        assertThrows(RepositoryException.class,
                () -> missingAnswer.repository.updateWithAnswers(question(9, 7), answers(true)));

        Fixture rollbackFailure = new Fixture();
        when(rollbackFailure.questionStatement.executeUpdate()).thenReturn(0);
        doThrow(new RepositoryException("rollback failed", null))
                .when(rollbackFailure.dbConnector).rollback(rollbackFailure.connection);
        RepositoryException failure = assertThrows(RepositoryException.class,
                () -> rollbackFailure.repository.updateWithAnswers(question(9, 7), answers(true)));
        assertEquals(1, failure.getSuppressed().length);

        Fixture shortBatch = new Fixture();
        when(shortBatch.questionStatement.executeUpdate()).thenReturn(1);
        when(shortBatch.answerStatement.executeBatch()).thenReturn(new int[]{1});
        assertThrows(RepositoryException.class,
                () -> shortBatch.repository.updateWithAnswers(question(9, 7), answers(true)));

        Fixture updateSetupFailure = new Fixture();
        doThrow(new SQLException("failed")).when(updateSetupFailure.connection).setAutoCommit(false);
        assertThrows(RepositoryException.class,
                () -> updateSetupFailure.repository.updateWithAnswers(question(9, 7), answers(true)));
    }

    private static Question question(int id, int quizId) {
        Question question = new Question();
        question.setId(id);
        question.setQuizId(quizId);
        question.setQuestion("Question");
        return question;
    }

    private static List<Answer> answers(boolean persisted) {
        return java.util.stream.IntStream.range(0, 4).mapToObj(index -> {
            Answer answer = new Answer();
            answer.setId(persisted ? index + 1 : 0);
            answer.setAnswer("Answer " + index);
            answer.setCorrect(index == 0);
            return answer;
        }).toList();
    }

    private static final class Fixture {
        private final DbConnector dbConnector = mock(DbConnector.class);
        private final Connection connection = mock(Connection.class);
        private final PreparedStatement questionStatement = mock(PreparedStatement.class);
        private final PreparedStatement answerStatement = mock(PreparedStatement.class);
        private final ResultSet generatedKeys = mock(ResultSet.class);
        private final QuestionRepositoryImpl repository = new QuestionRepositoryImpl(dbConnector);

        private Fixture() throws SQLException {
            when(dbConnector.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString(), anyInt())).thenReturn(questionStatement);
            when(connection.prepareStatement("UPDATE questions SET question = ?, quiz_id = ? WHERE id = ?"))
                    .thenReturn(questionStatement);
            when(connection.prepareStatement("INSERT INTO answers (answer, correct, question_id) VALUES (?, ?, ?)"))
                    .thenReturn(answerStatement);
            when(connection.prepareStatement("UPDATE answers SET answer = ?, correct = ? WHERE id = ? AND question_id = ?"))
                    .thenReturn(answerStatement);
            when(questionStatement.getGeneratedKeys()).thenReturn(generatedKeys);
        }
    }
}
