package ua.nure.latysh.quizzes.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final RateLimitService rateLimitService;
    private final ApiErrorWriter errorWriter;
    private final SecurityProperties properties;

    public RateLimitFilter(
            RateLimitService rateLimitService,
            ApiErrorWriter errorWriter,
            SecurityProperties properties) {
        this.rateLimitService = rateLimitService;
        this.errorWriter = errorWriter;
        this.properties = properties;
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
        boolean login = LOGIN_PATH.equals(request.getRequestURI());
        int limit = login
                ? properties.rateLimit().loginAttempts()
                : properties.rateLimit().requests();
        String key = request.getRemoteAddr() + (login ? ":login" : ":api");
        RateLimitDecision decision = rateLimitService.acquire(key, limit, properties.rateLimit().window());

        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
