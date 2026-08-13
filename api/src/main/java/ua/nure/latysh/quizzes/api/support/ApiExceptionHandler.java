package ua.nure.latysh.quizzes.api.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final Clock clock;

    public ApiExceptionHandler() {
        this(Clock.systemUTC());
    }

    ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> invalidRequest(ConstraintViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authenticationFailed(AuthenticationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Invalid username or password", request.getRequestURI());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(clock), status.value(), status.getReasonPhrase(), message, path));
    }
}
