package ua.nure.latysh.quizzes.api.support;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(requiredProperties = {"timestamp", "status", "error", "message", "path"})
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}

