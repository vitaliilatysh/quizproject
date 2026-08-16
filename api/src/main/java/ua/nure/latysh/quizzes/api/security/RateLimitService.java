package ua.nure.latysh.quizzes.api.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RateLimitService {
    private final Clock clock;
    private final int maxClients;
    private final Map<String, Window> windows = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public RateLimitService(SecurityProperties properties) {
        this(Clock.systemUTC(), properties.rateLimit().maxClients());
    }

    RateLimitService(Clock clock, int maxClients) {
        this.clock = clock;
        this.maxClients = maxClients;
    }

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
