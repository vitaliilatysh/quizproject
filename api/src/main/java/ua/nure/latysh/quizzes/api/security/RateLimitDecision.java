package ua.nure.latysh.quizzes.api.security;

public record RateLimitDecision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
}
