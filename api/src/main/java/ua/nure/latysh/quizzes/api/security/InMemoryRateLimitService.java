package ua.nure.latysh.quizzes.api.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "quiz.security.rate-limit", name = "backend", havingValue = "memory")
public class InMemoryRateLimitService implements RateLimitService {
    private final Clock clock;
    private final int maxClients;
    private final Map<String, Window> windows = new LinkedHashMap<>(16, 0.75f, true);

    public InMemoryRateLimitService(SecurityProperties properties) {
        this(Clock.systemUTC(), properties.rateLimit().maxClients());
    }

    InMemoryRateLimitService(Clock clock, int maxClients) {
        this.clock = clock;
        this.maxClients = maxClients;
    }

    @Override
    public synchronized RateLimitDecision acquire(String key, int limit, Duration windowDuration) {
        long now = clock.millis();
        Window window = windows.get(key);
        if (window == null || now >= window.resetAt()) {
            if (window == null && windows.size() >= maxClients) {
                String oldestKey = windows.keySet().iterator().next();
                windows.remove(oldestKey);
            }
            window = new Window(0, now + windowDuration.toMillis());
        }

        int requests = window.requests() + 1;
        windows.put(key, new Window(requests, window.resetAt()));
        int remaining = Math.max(0, limit - requests);
        long retryAfter = Math.max(1, (window.resetAt() - now + 999) / 1000);
        return new RateLimitDecision(requests <= limit, limit, remaining, retryAfter);
    }

    private record Window(int requests, long resetAt) {
    }
}
