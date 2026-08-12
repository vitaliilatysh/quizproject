package ua.nure.latysh.quizzes.repositories.impl;

import org.junit.Test;
import ua.nure.latysh.quizzes.db.connector.DbConnector;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.exceptions.QuizSubmissionException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AttemptRepositorySecurityTest {

    private static final Date COMPLETED_AT = new Date(1_700_000_000_000L);
    private static final Date FUTURE = new Date(COMPLETED_AT.getTime() + 60_000);

    @Test
    public void completionScoresOwnedAnswersAndCommitsOneTransaction() throws Exception {
        CompletionFixture fixture = new CompletionFixture(true, false, FUTURE,
                new int[]{1, 1, 2}, new int[]{10, 11, 20}, new boolean[]{true, false, true}, 1);
        AttemptRepositoryImpl repository = new AttemptRepositoryImpl(fixture.dbConnector);

        Attempt completed = repository.complete(5, 7, Set.of(10, 20), COMPLETED_AT);

        assertEquals(100, completed.getScore());
        assertEquals(COMPLETED_AT, completed.getEndTime());
        assertTrue(completed.isCompleted());
        verify(fixture.connection).setAutoCommit(false);
        verify(fixture.insertStatement, times(2)).addBatch();
        verify(fixture.insertStatement).executeBatch();
        verify(fixture.connection).commit();
        verify(fixture.dbConnector).close(fixture.connection, null, null);
    }

    @Test
    public void completionHandlesEmptySelectionsAndQuizzesWithoutQuestions() throws Exception {
        CompletionFixture unanswered = new CompletionFixture(true, false, FUTURE,
                new int[]{1}, new int[]{10}, new boolean[]{true}, 1);
        Attempt incomplete = new AttemptRepositoryImpl(unanswered.dbConnector)
                .complete(5, 7, Set.of(), COMPLETED_AT);
        assertEquals(0, incomplete.getScore());

        CompletionFixture noCorrectAnswer = new CompletionFixture(true, false, FUTURE,
                new int[]{1}, new int[]{10}, new boolean[]{false}, 1);
        Attempt malformedQuestion = new AttemptRepositoryImpl(noCorrectAnswer.dbConnector)
                .complete(5, 7, Set.of(), COMPLETED_AT);
        assertEquals(0, malformedQuestion.getScore());

        CompletionFixture emptyQuiz = new CompletionFixture(true, false, FUTURE,
                new int[0], new int[0], new boolean[0], 1);
        Attempt empty = new AttemptRepositoryImpl(emptyQuiz.dbConnector)
                .complete(5, 7, Set.of(), COMPLETED_AT);
        assertEquals(0, empty.getScore());
    }

    @Test
    public void completionRejectsMissingCompletedExpiredForeignAndConcurrentSubmissions() throws Exception {
        expectReason(new CompletionFixture(false, false, FUTURE,
                        new int[0], new int[0], new boolean[0], 1), Set.of(),
                QuizSubmissionException.Reason.NOT_FOUND);
        expectReason(new CompletionFixture(true, true, FUTURE,
                        new int[0], new int[0], new boolean[0], 1), Set.of(),
                QuizSubmissionException.Reason.ALREADY_COMPLETED);
        expectReason(new CompletionFixture(true, false, null,
                        new int[0], new int[0], new boolean[0], 1), Set.of(),
                QuizSubmissionException.Reason.EXPIRED);
        expectReason(new CompletionFixture(true, false, new Date(COMPLETED_AT.getTime() - 1),
                        new int[0], new int[0], new boolean[0], 1), Set.of(),
                QuizSubmissionException.Reason.EXPIRED);
        expectReason(new CompletionFixture(true, false, FUTURE,
                        new int[]{1}, new int[]{10}, new boolean[]{true}, 1), Set.of(999),
                QuizSubmissionException.Reason.INVALID_ANSWER);
        expectReason(new CompletionFixture(true, false, FUTURE,
                        new int[]{1}, new int[]{10}, new boolean[]{true}, 0), Set.of(10),
                QuizSubmissionException.Reason.ALREADY_COMPLETED);
    }

    @Test
    public void completionRollsBackSqlFailuresAndFailsWhenNoConnectionExists() throws Exception {
        CompletionFixture fixture = new CompletionFixture(true, false, FUTURE,
                new int[0], new int[0], new boolean[0], 1);
        when(fixture.connection.prepareStatement(anyString())).thenThrow(new SQLException("failed"));
        try {
            new AttemptRepositoryImpl(fixture.dbConnector).complete(5, 7, Set.of(), COMPLETED_AT);
            fail("Expected JDBC failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("complete attempt"));
        }
        verify(fixture.dbConnector).rollback(fixture.connection);
        verify(fixture.dbConnector).close(fixture.connection, null, null);

        DbConnector unavailable = mock(DbConnector.class);
        when(unavailable.getConnection()).thenReturn(null);
        try {
            new AttemptRepositoryImpl(unavailable).complete(5, 7, Set.of(), COMPLETED_AT);
            fail("Expected missing connection failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("unavailable"));
        }
    }

    @Test
    public void createFailsClosedWhenJdbcReturnsNoGeneratedIdentifier() throws Exception {
        DbConnector dbConnector = mock(DbConnector.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet generatedKeys = mock(ResultSet.class);
        when(dbConnector.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(statement);
        when(statement.getGeneratedKeys()).thenReturn(generatedKeys);
        when(generatedKeys.next()).thenReturn(false);
        Attempt attempt = new Attempt();
        attempt.setStartTime(COMPLETED_AT);
        attempt.setExpiresAt(FUTURE);

        try {
            new AttemptRepositoryImpl(dbConnector).create(attempt);
            fail("Expected missing generated id failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("generated id"));
        }
    }

    private void expectReason(CompletionFixture fixture, Set<Integer> answers,
                              QuizSubmissionException.Reason reason) {
        try {
            new AttemptRepositoryImpl(fixture.dbConnector).complete(5, 7, answers, COMPLETED_AT);
            fail("Expected " + reason);
        } catch (QuizSubmissionException expected) {
            assertEquals(reason, expected.getReason());
        }
        verify(fixture.dbConnector).rollback(fixture.connection);
        verify(fixture.dbConnector).close(fixture.connection, null, null);
    }

    private static final class CompletionFixture {
        private final DbConnector dbConnector = mock(DbConnector.class);
        private final Connection connection = mock(Connection.class);
        private final PreparedStatement lockStatement = mock(PreparedStatement.class);
        private final PreparedStatement answersStatement = mock(PreparedStatement.class);
        private final PreparedStatement insertStatement = mock(PreparedStatement.class);
        private final PreparedStatement completeStatement = mock(PreparedStatement.class);
        private final ResultSet lockResult = mock(ResultSet.class);
        private final ResultSet answersResult = mock(ResultSet.class);

        private CompletionFixture(boolean found, boolean completed, Date expiresAt,
                                  int[] questionIds, int[] answerIds, boolean[] correct,
                                  int completionUpdates) throws Exception {
            when(dbConnector.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                if (sql.contains("FOR UPDATE")) {
                    return lockStatement;
                }
                if (sql.startsWith("SELECT q.id")) {
                    return answersStatement;
                }
                if (sql.startsWith("INSERT INTO results")) {
                    return insertStatement;
                }
                return completeStatement;
            });
            when(lockStatement.executeQuery()).thenReturn(lockResult);
            when(lockResult.next()).thenReturn(found);
            when(lockResult.getInt("id")).thenReturn(5);
            when(lockResult.getInt("quiz_id")).thenReturn(3);
            when(lockResult.getInt("user_id")).thenReturn(7);
            when(lockResult.getTimestamp("start_time")).thenReturn(new Timestamp(COMPLETED_AT.getTime() - 60_000));
            when(lockResult.getTimestamp("expires_at"))
                    .thenReturn(expiresAt == null ? null : new Timestamp(expiresAt.getTime()));
            when(lockResult.getBoolean("completed")).thenReturn(completed);

            when(answersStatement.executeQuery()).thenReturn(answersResult);
            AtomicInteger row = new AtomicInteger(-1);
            when(answersResult.next()).thenAnswer(invocation -> row.incrementAndGet() < answerIds.length);
            when(answersResult.getInt("question_id")).thenAnswer(invocation -> questionIds[row.get()]);
            when(answersResult.getInt("answer_id")).thenAnswer(invocation -> answerIds[row.get()]);
            when(answersResult.getBoolean("correct")).thenAnswer(invocation -> correct[row.get()]);
            when(completeStatement.executeUpdate()).thenReturn(completionUpdates);
        }
    }
}
