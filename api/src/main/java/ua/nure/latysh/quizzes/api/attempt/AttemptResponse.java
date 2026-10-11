package ua.nure.latysh.quizzes.api.attempt;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(requiredProperties = {"attemptId", "quizId", "startedAt", "expiresAt", "completed", "score", "completedAt", "questions"})
public record AttemptResponse(
        int attemptId,
        int quizId,
        Instant startedAt,
        Instant expiresAt,
        boolean completed,
        @Schema(types = {"integer", "null"}) Integer score,
        @Schema(types = {"string", "null"}, format = "date-time") Instant completedAt,
        List<AttemptQuestionResponse> questions) {
}

