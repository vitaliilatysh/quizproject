package ua.nure.latysh.quizzes.api.security;

import java.time.Duration;

public interface RateLimitService {
    RateLimitDecision acquire(String key, int limit, Duration windowDuration);
}
