package ua.nure.latysh.quizzes.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final ApiErrorWriter errorWriter;
    private final SecurityProperties properties;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(
            RateLimitService rateLimitService,
            ClientIpResolver clientIpResolver,
            ApiErrorWriter errorWriter,
            SecurityProperties properties,
            MeterRegistry meterRegistry) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.errorWriter = errorWriter;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || CorsUtils.isPreFlightRequest(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        boolean sensitiveAuthentication = LOGIN_PATH.equals(request.getRequestURI())
                || REGISTER_PATH.equals(request.getRequestURI());
        int limit = sensitiveAuthentication
                ? properties.rateLimit().loginAttempts()
                : properties.rateLimit().requests();
        String scope = sensitiveAuthentication ? "auth" : "api";
        String key = scope + ":" + clientIpResolver.resolve(request);
        RateLimitDecision decision;
        try {
            decision = rateLimitService.acquire(key, limit, properties.rateLimit().window());
        } catch (DataAccessException exception) {
            recordMetric(scope, "unavailable");
            errorWriter.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Rate limiting is temporarily unavailable");
            return;
        }

        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        if (!decision.allowed()) {
            recordMetric(scope, "blocked");
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
            return;
        }
        recordMetric(scope, "allowed");
        filterChain.doFilter(request, response);
    }

    private void recordMetric(String scope, String outcome) {
        meterRegistry.counter("quiz.rate.limit.requests", "scope", scope, "outcome", outcome).increment();
    }
}
