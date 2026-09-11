package ua.nure.latysh.quizzes.api.support;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The error dispatch answers in the same shape as everything else.
 *
 * <p>Unit tests rather than through a server because the interesting inputs are
 * the request attributes the container sets, and those can be stated directly.
 * {@code ErrorDispatchIntegrationTest} covers a real filter failure reaching it.
 */
class ApiErrorControllerTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void reportsTheStatusAndTheOriginalPathTheContainerRecorded() {
        var response = new ApiErrorController(FIXED).handleError(errorRequest(500, "/api/v1/quizzes"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().status());
        assertEquals("Unexpected server error", response.getBody().message());
        assertEquals("/api/v1/quizzes", response.getBody().path());
        assertEquals(Instant.parse("2026-08-13T12:00:00Z"), response.getBody().timestamp());
    }

    @Test
    void aClientErrorKeepsItsOwnMeaningRatherThanReadingAsAFault() {
        var response = new ApiErrorController(FIXED).handleError(errorRequest(404, "/api/v1/missing"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not Found", response.getBody().message());
    }

    /**
     * Nothing usable in the attributes. A container that re-dispatched here at
     * all has something wrong with it, so the answer is a fault rather than a
     * guess — and the request's own URI is the closest thing to the truth left.
     */
    @Test
    void anErrorWithNothingRecordedIsStillAnswered() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn("not an integer");
        when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/error");

        var response = new ApiErrorController(FIXED).handleError(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("/error", response.getBody().path());
    }

    @Test
    void aStatusOutsideTheEnumIsReportedAsAFault() {
        var response = new ApiErrorController(FIXED).handleError(errorRequest(799, "/api/v1/quizzes"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    private static HttpServletRequest errorRequest(int status, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(status);
        when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(path);
        return request;
    }
}
