package ua.nure.latysh.quizzes.api.attempt;

import java.time.Instant;
import java.util.List;

public record AttemptResponse(
        int attemptId,
        int quizId,
        Instant startedAt,
        Instant expiresAt,
        boolean completed,
        Integer score,
        Instant completedAt,
        List<AttemptQuestionResponse> questions) {
}

