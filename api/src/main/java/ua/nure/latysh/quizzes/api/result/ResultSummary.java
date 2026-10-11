package ua.nure.latysh.quizzes.api.result;

import io.swagger.v3.oas.annotations.media.Schema;

/** Aggregates over every completed attempt owned by the authenticated user. */
@Schema(requiredProperties = {"totalAttempts", "averageScore", "bestScore"})
public record ResultSummary(long totalAttempts, double averageScore, int bestScore) {
}
