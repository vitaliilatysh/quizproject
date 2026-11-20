package ua.nure.latysh.quizzes.api.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public interface ResultAdminModels {
    @Schema(name = "AdminResult", requiredProperties = {"attemptId", "username", "quizId", "quizName", "score", "completedAt"})
    record ResultResponse(
            long attemptId,
            String username,
            int quizId,
            String quizName,
            int score,
            Instant completedAt) {
    }
}
