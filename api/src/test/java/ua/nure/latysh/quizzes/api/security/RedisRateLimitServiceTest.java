package ua.nure.latysh.quizzes.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class RedisRateLimitServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void convertsTheAtomicRedisResultIntoADecision() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        doReturn(List.of(1L, 2_000L), List.of(2L, 500L))
                .when(template)
                .execute(any(RedisScript.class), eq(List.of("quiz:rate-limit:auth:client")), eq("2000"));
        RedisRateLimitService service = new RedisRateLimitService(template);

        RateLimitDecision allowed = service.acquire("auth:client", 1, Duration.ofSeconds(2));
        RateLimitDecision blocked = service.acquire("auth:client", 1, Duration.ofSeconds(2));

        assertTrue(allowed.allowed());
        assertEquals(0, allowed.remaining());
        assertEquals(2, allowed.retryAfterSeconds());
        assertFalse(blocked.allowed());
        assertEquals(0, blocked.remaining());
        assertEquals(1, blocked.retryAfterSeconds());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsAnInvalidRedisResult() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        doReturn(null)
                .when(template)
                .execute(any(RedisScript.class), any(List.class), any());
        RedisRateLimitService service = new RedisRateLimitService(template);

        assertThrows(IllegalStateException.class,
                () -> service.acquire("api:client", 10, Duration.ofMinutes(1)));
    }
}
