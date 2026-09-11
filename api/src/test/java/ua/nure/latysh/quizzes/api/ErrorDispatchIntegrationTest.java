package ua.nure.latysh.quizzes.api;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the container does with a failure the exception handler never sees.
 *
 * <p>{@code ApiExceptionHandler} covers anything thrown from a controller, but a
 * filter runs before the dispatcher, so a failure there leaves the servlet and
 * the container re-dispatches to {@code /error}. That forward is a request like
 * any other as far as the security chain is concerned, and
 * {@code anyRequest().denyAll()} was answering it 401 — so the caller was told to
 * authenticate because a filter had broken, and nothing was recorded as 5xx.
 *
 * <p>Real ports, because MockMvc has no error dispatch: it rethrows instead,
 * which is why this went unnoticed by a suite that otherwise covers the API
 * closely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:error_dispatch;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class ErrorDispatchIntegrationTest {
    private static final String BREAKING_PATH = "/api/v1/quizzes?break=true";

    @LocalServerPort
    private int port;

    @Test
    void aFilterFailureIsReportedAsAServerFault() throws Exception {
        HttpResponse<String> response = get(BREAKING_PATH);

        assertThat(response.statusCode())
                .as("a broken filter is a fault, not a request to authenticate")
                .isEqualTo(500);
        assertThat(response.body()).doesNotContain("brand of tea");
        // The same body every other error uses. Boot's own /error controller
        // answers in a different shape and, with server.error.include-message set
        // to never, omits message entirely — so a client parsing errors would meet
        // something it does not recognise exactly when things are worst.
        assertThat(response.body())
                .contains("\"status\":500")
                .contains("\"message\":\"Unexpected server error\"")
                .contains("\"path\":\"/api/v1/quizzes\"")
                .contains("\"timestamp\"")
                .contains("\"error\":\"Internal Server Error\"");
    }

    /**
     * The matcher is on the dispatcher type, not on the path, and this is the
     * difference: the container's own forward is allowed through, a request
     * somebody aims at /error themselves is not.
     */
    @Test
    void errorIsStillNotSomethingAnyoneCanRequest() throws Exception {
        assertThat(get("/error").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class BreakingFilterConfiguration {
        /**
         * Registered outside the security chain so it fails where no
         * {@code @ExceptionHandler} can reach — which is the case under test.
         */
        @Bean
        FilterRegistrationBean<Filter> breakingFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(new Filter() {
                @Override
                public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                        throws IOException, ServletException {
                    if ("break=true".equals(((HttpServletRequest) request).getQueryString())) {
                        throw new IllegalStateException("the wrong brand of tea");
                    }
                    chain.doFilter(request, response);
                }
            });
            registration.setOrder(Integer.MIN_VALUE);
            return registration;
        }
    }
}
