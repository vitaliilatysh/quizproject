package ua.nure.latysh.quizzes.api.account;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(requiredProperties = {"username", "firstName", "lastName", "role", "status", "registeredAt", "lastLoginAt"})
public record ProfileResponse(
        String username,
        String firstName,
        String lastName,
        String role,
        String status,
        Instant registeredAt,
        @Schema(types = {"string", "null"}, format = "date-time") Instant lastLoginAt) {
}
