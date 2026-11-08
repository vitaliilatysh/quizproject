package ua.nure.latysh.quizzes.api.attempt;

import org.junit.jupiter.api.Test;
import ua.nure.latysh.quizzes.api.domain.AnswerRepository;
import ua.nure.latysh.quizzes.api.domain.Attempt;
import ua.nure.latysh.quizzes.api.domain.AttemptQuestion;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.domain.ResultRepository;
import ua.nure.latysh.quizzes.api.observability.QuizMetrics;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttemptSubmissionSizeTest {
    @Test
    void rejectsAnOversizedSubmissionWithoutIteratingOrCopyingIt() {
        var attempts = mock(AttemptRepository.class);
        var answers = mock(AnswerRepository.class);
        var results = mock(ResultRepository.class);
        var snapshots = mock(AttemptSnapshotStore.class);
        var metrics = mock(QuizMetrics.class);
        var attempt = mock(Attempt.class);
        Instant now = Instant.parse("2026-10-18T00:00:00Z");
        when(attempts.findByIdAndUserLoginForUpdate(1, "student")).thenReturn(Optional.of(attempt));
        when(attempt.getExpiresAt()).thenReturn(now.plusSeconds(60));
        when(snapshots.find(attempt)).thenReturn(List.of(
                new AttemptQuestion(attempt, 1, "Question", 1, "Answer", true)));
        var completer = new AttemptCompleter(attempts, answers, results, snapshots,
                Clock.fixed(now, ZoneOffset.UTC), metrics);

        Set<Integer> submitted = new AbstractSet<>() {
            @Override
            public int size() {
                return Integer.MAX_VALUE;
            }

            @Override
            public Iterator<Integer> iterator() {
                throw new AssertionError("Oversized submissions must be rejected before reading their elements");
            }
        };

        assertThatThrownBy(() -> completer.complete(1, "student", submitted))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(answers, results, metrics);
    }
}
