package ua.nure.latysh.quizzes.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import ua.nure.latysh.quizzes.api.support.ApiError;

import java.io.IOException;
import java.time.Clock;

@Component
public class ApiErrorWriter {
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApiErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                clock.instant(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
