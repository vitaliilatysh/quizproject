package ua.nure.latysh.quizzes.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoginAttemptLimiter {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        AttemptWindow window = attempts.get(key);
        if (window == null) {
            return false;
        }
        if (window.expired(clock.instant())) {
            attempts.remove(key, window);
            return false;
        }
        return window.failures >= MAX_FAILURES;
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.expired(now)) {
                return new AttemptWindow(1, now);
            }
            return new AttemptWindow(current.failures + 1, current.startedAt);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private static final class AttemptWindow {
        private final int failures;
        private final Instant startedAt;

        private AttemptWindow(int failures, Instant startedAt) {
            this.failures = failures;
            this.startedAt = startedAt;
        }

        private boolean expired(Instant now) {
            return !now.isBefore(startedAt.plus(WINDOW));
        }
    }
}
