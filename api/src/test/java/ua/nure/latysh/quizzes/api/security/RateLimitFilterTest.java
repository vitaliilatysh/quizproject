package ua.nure.latysh.quizzes.api.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ua.nure.latysh.quizzes.api.config.SecurityProperties.RateLimitProperties.Backend.REDIS;

class RateLimitFilterTest {
    @Test
    void returnsServiceUnavailableAndRecordsAMetricWhenRedisFails() throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        ApiErrorWriter errorWriter = mock(ApiErrorWriter.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SecurityProperties properties = properties();
        RateLimitFilter filter = new RateLimitFilter(service, resolver, errorWriter, properties, registry);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(resolver.resolve(request)).thenReturn("203.0.113.7");
        doThrow(new DataAccessResourceFailureException("Redis is unavailable"))
                .when(service)
                .acquire("auth:203.0.113.7", 3, Duration.ofMinutes(1));

        filter.doFilter(request, response, new MockFilterChain());

        verify(errorWriter).write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                "Rate limiting is temporarily unavailable");
        assertEquals(1.0, registry.get("quiz.rate.limit.requests")
                .tags("scope", "auth", "outcome", "unavailable")
                .counter()
                .count());
    }

    // Which requests the limiter looks at at all. A CORS preflight is the
    // browser asking permission before the real request, and the real request
    // is counted a moment later — counting both would halve every caller's
    // budget for no reason, and a blocked preflight fails the request that
    // follows it with a CORS error that names nothing.
    @Test
    void countsApiRequestsAndSkipsEverythingElse() {
        RateLimitFilter filter = new RateLimitFilter(mock(RateLimitService.class),
                mock(ClientIpResolver.class), mock(ApiErrorWriter.class), properties(),
                new SimpleMeterRegistry());

        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/quizzes")),
                "an API request went uncounted");
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health")),
                "the health probe was counted against a caller's budget");

        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/quizzes");
        preflight.addHeader("Origin", "https://example.test");
        preflight.addHeader("Access-Control-Request-Method", "GET");
        assertTrue(filter.shouldNotFilter(preflight), "a preflight was counted as a request of its own");
    }

    /**
     * Which budget each password-verifying endpoint is charged against.
     *
     * <p>Asserted by the limit the filter asks for rather than by the metric
     * tag, because the limit is the thing that matters: the tag could read
     * "auth" while the call still carried the general allowance.
     */
    @Test
    void chargesEveryEndpointThatChecksAPasswordAgainstTheStrictBudget() throws Exception {
        for (String path : List.of("/api/v1/auth/login", "/api/v1/auth/register",
                "/api/v1/users/me/password")) {
            assertEquals(3, limitAskedFor(path), path + " was not charged the login budget");
        }
        assertEquals(100, limitAskedFor("/api/v1/quizzes"),
            "an ordinary read was charged the login budget");
    }

    private static int limitAskedFor(String path) throws Exception {
        RateLimitService service = mock(RateLimitService.class);
        ClientIpResolver resolver = mock(ClientIpResolver.class);
        RateLimitFilter filter = new RateLimitFilter(service, resolver, mock(ApiErrorWriter.class),
                properties(), new SimpleMeterRegistry());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        when(resolver.resolve(request)).thenReturn("203.0.113.7");
        when(service.acquire(anyString(), anyInt(), any()))
                .thenReturn(new RateLimitDecision(true, 1, 1, 0));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(service).acquire(anyString(), limit.capture(), any());
        return limit.getValue();
    }

    private static SecurityProperties properties() {
        return new SecurityProperties("secret", "issuer", Duration.ofMinutes(15), Duration.ofDays(7),
                List.of("https://example.test"),
                new SecurityProperties.RateLimitProperties(
                        REDIS, 100, 3, Duration.ofMinutes(1), 100, List.of("127.0.0.1/32")));
    }
}
