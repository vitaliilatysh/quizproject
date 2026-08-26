package ua.nure.latysh.quizzes.api.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = correlationId(request.getHeader(HEADER_NAME));
        response.setHeader(HEADER_NAME, correlationId);
        long startedAt = System.nanoTime();
        try (var ignored = MDC.putCloseable(MDC_KEY, correlationId)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                var completionLog = request.getRequestURI().startsWith("/actuator/health")
                        ? LOGGER.atDebug()
                        : LOGGER.atInfo();
                completionLog
                        .addKeyValue("http.request.method", request.getMethod())
                        .addKeyValue("url.path", request.getRequestURI())
                        .addKeyValue("http.response.status_code", response.getStatus())
                        .addKeyValue("duration_ms", elapsedMilliseconds(startedAt))
                        .log("HTTP request completed");
            }
        }
    }

    private static String correlationId(String requestedId) {
        return requestedId != null && SAFE_CORRELATION_ID.matcher(requestedId).matches()
                ? requestedId
                : UUID.randomUUID().toString();
    }

    private static long elapsedMilliseconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
