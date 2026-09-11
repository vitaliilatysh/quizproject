package ua.nure.latysh.quizzes.api.support;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

/**
 * The one error response {@code ApiExceptionHandler} cannot produce.
 *
 * <p>An {@code @ExceptionHandler} only sees what reaches the dispatcher. A
 * filter runs before it, so a failure there leaves the servlet entirely and the
 * container re-dispatches here — and Boot's own controller answers in a
 * different shape from the rest of the API, without the {@code message} field at
 * all, because {@code server.error.include-message} is set to never. A client
 * parsing errors would meet a body it does not recognise exactly when something
 * has gone badly wrong.
 *
 * <p>Defining this bean is what stands Boot's down; it reads the same request
 * attributes and answers in the shape every other error uses. What it cannot
 * recover is the correlation id when the failure happened before the filter that
 * assigns one — there is nothing to report in that case, and an invented value
 * would be worse than none.
 */
@RestController
public class ApiErrorController implements ErrorController {
    private final Clock clock;

    public ApiErrorController() {
        this(Clock.systemUTC());
    }

    ApiErrorController(Clock clock) {
        this.clock = clock;
    }

    @RequestMapping(value = "${server.error.path:/error}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiError> handleError(HttpServletRequest request) {
        HttpStatus status = statusOf(request);
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                status.is5xxServerError() ? "Unexpected server error" : status.getReasonPhrase(),
                path(request)));
    }

    private static HttpStatus statusOf(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer statusCode) {
            HttpStatus resolved = HttpStatus.resolve(statusCode);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * The path that failed, not {@code /error}. The container keeps the original
     * on the request; without it every fault would be reported against the same
     * URI, which is what made these indistinguishable in the logs.
     */
    private static String path(HttpServletRequest request) {
        Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return original instanceof String uri ? uri : request.getRequestURI();
    }
}
