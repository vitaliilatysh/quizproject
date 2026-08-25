package ua.nure.latysh.quizzes.api.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {
    @Test
    void tracksAndResetsAFixedWindow() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1_000L, 1_500L, 3_100L);
        RateLimitService service = new InMemoryRateLimitService(clock, 10);

        RateLimitDecision first = service.acquire("client", 1, Duration.ofSeconds(2));
        RateLimitDecision blocked = service.acquire("client", 1, Duration.ofSeconds(2));
        RateLimitDecision reset = service.acquire("client", 1, Duration.ofSeconds(2));

        assertTrue(first.allowed());
        assertEquals(0, first.remaining());
        assertEquals(2, first.retryAfterSeconds());
        assertFalse(blocked.allowed());
        assertEquals(2, blocked.retryAfterSeconds());
        assertTrue(reset.allowed());
    }

    @Test
    void evictsTheLeastRecentlyUsedClientAtCapacity() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1_000L);
        RateLimitService service = new InMemoryRateLimitService(clock, 1);

        assertTrue(service.acquire("first", 1, Duration.ofMinutes(1)).allowed());
        assertTrue(service.acquire("second", 1, Duration.ofMinutes(1)).allowed());
        assertTrue(service.acquire("first", 1, Duration.ofMinutes(1)).allowed());
    }
}
