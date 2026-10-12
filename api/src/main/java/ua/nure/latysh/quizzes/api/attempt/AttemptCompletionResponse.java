package ua.nure.latysh.quizzes.api.attempt;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(requiredProperties = {"attemptId", "quizId", "score", "completedAt"})
public record AttemptCompletionResponse(int attemptId, int quizId, int score, Instant completedAt) {
}

