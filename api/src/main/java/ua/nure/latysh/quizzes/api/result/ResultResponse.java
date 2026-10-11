package ua.nure.latysh.quizzes.api.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(requiredProperties = {"attemptId", "quizId", "quizName", "score", "completedAt"})
public record ResultResponse(
        int attemptId,
        int quizId,
        String quizName,
        int score,
        Instant completedAt
) {
}

