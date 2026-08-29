package ua.nure.latysh.quizzes.api.attempt;

import java.time.Instant;

public record AttemptCompletionResponse(int attemptId, int quizId, int score, Instant completedAt) {
}

