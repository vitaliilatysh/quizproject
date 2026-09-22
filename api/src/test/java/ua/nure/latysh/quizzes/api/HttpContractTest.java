package ua.nure.latysh.quizzes.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ua.nure.latysh.quizzes.api.support.ApiExceptionHandler;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API contract for what the API answers before any handler runs: CORS, request shape, content negotiation and what is served where. */
class HttpContractTest extends ApiContractTestBase {
    @Test
    void appliesCorsAllowlistWithoutRateLimitingPreflightRequests() throws Exception {
        mockMvc.perform(options("/api/v1/results/me")
                        .header(HttpHeaders.ORIGIN, "https://app.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString("X-Correlation-ID")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString("X-Total-Count")))
                .andExpect(header().doesNotExist("X-RateLimit-Limit"));

        mockMvc.perform(options("/api/v1/results/me")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    /**
     * The request-shape failures, each with its own status and the API's own body.
     *
     * <p>All four used to reach the client as 401. Spring raises them before a
     * controller runs, so they left the dispatcher, the container forwarded to
     * {@code /error}, and the security chain denied that forward — a malformed
     * body was answered with "Authentication is required", and {@code path} read
     * {@code /error} rather than what was asked for.
     *
     * <p>They are also why {@code ApiExceptionHandler} names them rather than
     * leaving them to Spring: the catch-all for {@code Exception} outranks
     * Spring's own resolver, so without these rows each of these would now be a
     * 500 instead.
     */
    @Test
    void requestShapeFailuresKeepTheirOwnStatus() throws Exception {
        // Allow is not decoration: RFC 9110 requires it on a 405, and it is the
        // only thing that tells the caller what to send instead. Building the
        // response by hand drops what Spring's own resolver would have set.
        mockMvc.perform(get("/api/v1/auth/login").with(from("192.0.2.120")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
                .andExpect(jsonPath("$.message").value("Method is not allowed for this resource"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));

        mockMvc.perform(post("/api/v1/auth/login").with(from("192.0.2.121"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));

        mockMvc.perform(post("/api/v1/auth/login").with(from("192.0.2.122"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("username=student"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Content type is not supported"));

        mockMvc.perform(get("/api/v1/quizzes/not-a-number").with(from("192.0.2.123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/quizzes/not-a-number"));
    }

    /**
     * A caller that accepts nothing this API produces is not a fault.
     *
     * <p>The status was 406 either way — a 500 built for it could not be written
     * in a type the caller accepts either, so Spring falls back to 406 with an
     * empty body. What the catch-all added was a log line: "Unhandled exception
     * serving GET /api/v1/quizzes" at ERROR, for somebody sending
     * {@code Accept: application/xml}. That is what this asserts, because the
     * status alone cannot tell the two apart.
     */
    @Test
    void anUnacceptableResponseTypeIsNotLoggedAsAFault() {
        Logger handlerLog = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        var recorded = new ListAppender<ILoggingEvent>();
        recorded.start();
        handlerLog.addAppender(recorded);
        try {
            mockMvc.perform(get("/api/v1/quizzes")
                            .accept(MediaType.APPLICATION_XML)
                            .with(from("192.0.2.124")))
                    .andExpect(status().isNotAcceptable());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        } finally {
            handlerLog.detachAppender(recorded);
        }

        assertThat(recorded.list)
                .as("a client's choice of Accept header is not a server fault")
                .noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    /**
     * Actuator moved to its own connector, so none of its paths are mapped here.
     * What it does serve, and to whom, is asserted on real ports by
     * {@link ActuatorPortIntegrationTest} — MockMvc cannot tell two connectors apart.
     */
    @Test
    void apiDocumentationIsPublicAndActuatorIsNotServedOnTheApiPort() throws Exception {
        // 404 where nothing is mapped, 401 where a security rule denies first
        // (/actuator/metrics). Either way the API port does not answer with it.
        for (String actuatorPath : new String[]{"/actuator/health", "/actuator/health/liveness",
                "/actuator/health/readiness", "/actuator/metrics", "/actuator/prometheus"}) {
            mockMvc.perform(get(actuatorPath))
                    .andExpect(status().is4xxClientError());
        }

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Quiz REST API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));

        mockMvc.perform(get("/unknown"))
                .andExpect(status().isUnauthorized());
    }
}
