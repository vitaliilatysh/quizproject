package ua.nure.latysh.quizzes.api.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final String REQUEST_VALIDATION_FAILED = "Request validation failed";
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ApiError> conflict(ResourceConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiError> invalidSubmission(InvalidRequestException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> invalidRequest(ConstraintViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, REQUEST_VALIDATION_FAILED, request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, REQUEST_VALIDATION_FAILED, request.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authenticationFailed(AuthenticationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Invalid username or password", request.getRequestURI());
    }

    /**
     * The request-shape failures Spring raises before a controller is reached.
     *
     * <p>They are listed rather than left to Spring's own resolver because the
     * catch-all below outranks it: an {@code @ExceptionHandler} for
     * {@code Exception} matches these too, and without these they would come
     * back 500. Each is a client mistake, and each is reachable — there is a
     * test per row, which is also what keeps this list from growing entries
     * nothing can trigger.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, REQUEST_VALIDATION_FAILED, request.getRequestURI());
    }

    /**
     * 405, carrying Allow.
     *
     * <p>RFC 9110 makes the header mandatory on this status, and it is the only
     * thing that tells a caller what to try instead. Spring's own resolver sets
     * it; a hand-built ResponseEntity has to repeat that, and dropping it is
     * easy to miss because the status alone looks right.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = exception.getSupportedHttpMethods();
        if (supported != null) {
            headers.setAllow(supported);
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(body(HttpStatus.METHOD_NOT_ALLOWED, "Method is not allowed for this resource",
                        request.getRequestURI()));
    }

    /**
     * The caller accepts nothing this API produces.
     *
     * <p>Named so that the catch-all does not log it. The response was already
     * 406 either way — a 500 built here cannot be written in a type the caller
     * accepts either, so Spring falls back to 406 with an empty body — but the
     * catch-all logged an ERROR on the way, and "Unhandled exception serving GET
     * /api/v1/quizzes" for somebody sending Accept: application/xml is a server
     * fault reported for a client's choice. The body stays empty: there is no
     * representation of it the caller would take.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<Void> notAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    /**
     * A path the security rules allow through to a dispatcher that maps nothing.
     *
     * <p>Reachable since actuator moved to its own connector: {@code /actuator/**}
     * is permitted on the API port and served on neither, so the dispatcher finds
     * no handler. Without this row the catch-all below would report a missing
     * path as a server fault — ApiContractTest caught exactly that.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noHandler(NoResourceFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Resource was not found", request.getRequestURI());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported",
                request.getRequestURI());
    }

    /**
     * Anything not named above: a fault, not a request the caller got wrong.
     *
     * <p>Without this the exception left the dispatcher, the container forwarded
     * to {@code /error}, and the security chain denied that forward — so a
     * failing database came back to the client as 401 with {@code path} reading
     * {@code /error}. The client is told to sign in again for a server fault,
     * which the web client obeys by dropping the session; and the 5xx rate alert
     * never sees it, because nothing was ever recorded as 5xx.
     *
     * <p>The message is fixed. Whatever the exception says — a connection string,
     * a constraint name, a stack frame — belongs in the log, under the
     * correlation id the response already carries, and not in the body.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedFailure(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled exception serving {} {}",
                request.getMethod(), request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request.getRequestURI());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(body(status, message, path));
    }

    private ApiError body(HttpStatus status, String message, String path) {
        return new ApiError(Instant.now(clock), status.value(), status.getReasonPhrase(), message, path);
    }
}

