package ua.nure.latysh.quizzes.api.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static SecurityProperties properties() {
        return new SecurityProperties("secret", "issuer", Duration.ofMinutes(15), List.of("https://example.test"),
                new SecurityProperties.RateLimitProperties(
                        REDIS, 100, 3, Duration.ofMinutes(1), 100, List.of("127.0.0.1/32")));
    }
}
