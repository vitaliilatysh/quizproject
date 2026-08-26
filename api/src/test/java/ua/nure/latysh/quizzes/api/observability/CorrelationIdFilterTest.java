package ua.nure.latysh.quizzes.api.observability;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesASafeCorrelationIdForTheResponseAndRequestLog() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/quizzes");
        var response = new MockHttpServletResponse();
        var observedCorrelationId = new AtomicReference<String>();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "client-request-123");

        filter.doFilter(request, response, (_, _) ->
                observedCorrelationId.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertEquals("client-request-123", response.getHeader(CorrelationIdFilter.HEADER_NAME));
        assertEquals("client-request-123", observedCorrelationId.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void replacesAnUnsafeCorrelationId() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        var response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "bad");

        filter.doFilter(request, response, (_, _) -> response.setStatus(204));

        String generatedId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertTrue(generatedId.matches("[a-f0-9-]{36}"));
        assertEquals(204, response.getStatus());
    }
}
