package ua.nure.latysh.quizzes.api.account;

import java.time.Instant;

public record ProfileResponse(
        String username,
        String firstName,
        String lastName,
        String role,
        String status,
        Instant registeredAt,
        Instant lastLoginAt) {
}
