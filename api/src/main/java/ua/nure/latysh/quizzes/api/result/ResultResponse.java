package ua.nure.latysh.quizzes.api.result;

import java.time.Instant;

public record ResultResponse(
        int attemptId,
        int quizId,
        String quizName,
        int score,
        Instant completedAt
) {
}

