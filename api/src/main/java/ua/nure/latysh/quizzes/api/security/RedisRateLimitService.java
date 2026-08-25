package ua.nure.latysh.quizzes.api.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "quiz.security.rate-limit", name = "backend", havingValue = "redis",
        matchIfMissing = true)
public class RedisRateLimitService implements RateLimitService {
    private static final String KEY_PREFIX = "quiz:rate-limit:";
    private static final RedisScript<List> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local requests = redis.call('INCR', KEYS[1])
            if requests == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {requests, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitDecision acquire(String key, int limit, Duration windowDuration) {
        List<?> result = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(KEY_PREFIX + key),
                Long.toString(windowDuration.toMillis()));
        if (result == null || result.size() != 2
                || !(result.get(0) instanceof Number requests)
                || !(result.get(1) instanceof Number ttlMillis)) {
            throw new IllegalStateException("Redis returned an invalid rate limit result");
        }

        int requestCount = requests.intValue();
        int remaining = Math.max(0, limit - requestCount);
        long retryAfter = Math.max(1, (ttlMillis.longValue() + 999) / 1000);
        return new RateLimitDecision(requestCount <= limit, limit, remaining, retryAfter);
    }
}
