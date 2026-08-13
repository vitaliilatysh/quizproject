package ua.nure.latysh.quizzes.api.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {
    @Test
    void usesTheProvidedClockForErrors() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC);
        ApiExceptionHandler handler = new ApiExceptionHandler(clock);
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/quizzes/99");

        var response = handler.notFound(new ResourceNotFoundException("missing"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(Instant.parse("2026-08-13T12:00:00Z"), response.getBody().timestamp());
    }
}

