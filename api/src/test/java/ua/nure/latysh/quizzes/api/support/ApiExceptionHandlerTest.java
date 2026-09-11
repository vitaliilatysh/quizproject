package ua.nure.latysh.quizzes.api.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * 405 with nothing to advertise.
     *
     * <p>Spring declares the supported set nullable, and it is null when the
     * exception is built without one. Allow must then be absent rather than
     * empty: an empty Allow means "no method is allowed here", which is a
     * different and wrong claim.
     */
    @Test
    void theAllowHeaderIsLeftOffWhenThereIsNothingToPutInIt() {
        ApiExceptionHandler handler = new ApiExceptionHandler(
                Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC));
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        var response = handler.methodNotAllowed(
                new org.springframework.web.HttpRequestMethodNotSupportedException("GET"), request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNull(response.getHeaders().getFirst("Allow"));
    }

    /**
     * The catch-all says nothing about what failed.
     *
     * <p>What an unexpected exception carries is whatever the failing library
     * chose to put in it — a connection string, a constraint name, a file path.
     * The client gets a fixed sentence and the correlation id it already has;
     * the exception goes to the log.
     */
    @Test
    void theCatchAllReportsAFaultWithoutRepeatingWhatItSaid() {
        ApiExceptionHandler handler = new ApiExceptionHandler(
                Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC));
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/quizzes");
        when(request.getMethod()).thenReturn("GET");

        var response = handler.unexpectedFailure(
                new IllegalStateException("jdbc:mysql://db.internal:3306 refused the connection"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Unexpected server error", response.getBody().message());
        assertEquals("/api/v1/quizzes", response.getBody().path());
        assertFalse(response.getBody().message().contains("db.internal"));
    }
}

