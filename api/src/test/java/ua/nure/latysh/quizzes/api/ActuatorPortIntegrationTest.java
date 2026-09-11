package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator answers on the management connector and nowhere else.
 *
 * <p>The API port and the management port are the whole point of this class, so it
 * runs a real server rather than MockMvc, which has no notion of which connector a
 * request arrived on.
 */
@ActiveProfiles("test")
// No properties attribute: MANAGEMENT_PORT comes from application-test.yml, which
// keeps this class on the same cached context as the other real-server tests. Its own
// property set would build a second context, and every context re-runs schema.sql
// against an H2 database that outlives the first one.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorPortIntegrationTest {
    private static final String SCRAPED_COUNTER = "quiz_authentication_attempts_total";

    @LocalServerPort
    private int apiPort;

    @Value("${local.management.port}")
    private int managementPort;

    @Test
    void theApiPortDoesNotServeTheMetricsRegistry() throws Exception {
        HttpResponse<String> scrape = get(apiPort, "/actuator/prometheus");

        assertThat(scrape.statusCode()).isNotEqualTo(200);
        assertThat(scrape.body()).doesNotContain(SCRAPED_COUNTER);
        assertThat(get(apiPort, "/actuator/metrics").statusCode()).isNotEqualTo(200);
        assertThat(get(apiPort, "/actuator/health").statusCode()).isNotEqualTo(200);
    }

    @Test
    void theManagementPortServesPrometheusToAnUnauthenticatedScraper() throws Exception {
        // One API call first: http.server.requests has no series until a request has
        // been served, and it is the series that carries a tag per API URI.
        assertThat(get(apiPort, "/api/v1/quizzes").statusCode()).isEqualTo(200);

        HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus");

        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body())
                .contains(SCRAPED_COUNTER)
                .contains("quiz_rate_limit_requests_total")
                .contains("http_server_requests_seconds")
                .contains("uri=\"/api/v1/quizzes\"");
    }

    @Test
    void theManagementPortKeepsTheMetricsEndpointAdministratorOnly() throws Exception {
        assertThat(get(managementPort, "/actuator/metrics").statusCode()).isEqualTo(401);

        HttpResponse<String> asAdministrator = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(uri(managementPort, "/actuator/metrics"))
                        .header("Authorization", "Bearer " + administratorToken())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(asAdministrator.statusCode()).isEqualTo(200);
        assertThat(asAdministrator.body()).contains("\"names\"");
    }

    @Test
    void theManagementPortAnswersTheKubeletProbes() throws Exception {
        assertThat(get(managementPort, "/actuator/health/liveness").body()).contains("\"UP\"");
        assertThat(get(managementPort, "/actuator/health/readiness").body()).contains("\"UP\"");
    }

    @Test
    void theManagementPortDoesNotServeTheApi() throws Exception {
        assertThat(get(managementPort, "/api/v1/quizzes").statusCode()).isNotEqualTo(200);
        assertThat(get(apiPort, "/api/v1/quizzes").statusCode()).isEqualTo(200);
    }

    private String administratorToken() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(uri(apiPort, "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"admin\",\"password\":\"secret123\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    private static HttpResponse<String> get(int port, String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(uri(port, path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(int port, String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
