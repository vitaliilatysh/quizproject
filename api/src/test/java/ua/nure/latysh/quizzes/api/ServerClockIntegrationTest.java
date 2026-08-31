package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The web client schedules an attempt's automatic submission against the
 * deadline this API issued, and it can only place that against its own clock.
 * A device minutes ahead of the server would end an attempt early; one behind
 * would submit past the deadline, and this API refuses a completion it stamps
 * after it. Reading Date is how the browser measures that difference.
 *
 * <p>Asserted through a real server rather than MockMvc: whether the response
 * carries a Date at all is the container's doing, not the application's, and
 * MockMvc never involves one.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerClockIntegrationTest {
    private static final String ALLOWED_ORIGIN = "https://app.example.test";

    @LocalServerPort
    private int port;

    @Test
    void tellsACrossOriginBrowserWhatTimeItIs() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/quizzes"))
                .header("Origin", ALLOWED_ORIGIN)
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        // Present, or there is nothing to expose.
        String date = response.headers().firstValue("Date").orElse(null);
        assertThat(date).as("the server sends no Date at all").isNotNull();

        // Readable, or a browser sees null however present it is: Date is not
        // one of the headers exposed to cross-origin scripts by default.
        String exposed = response.headers().firstValue("Access-Control-Expose-Headers").orElse("");
        assertThat(Arrays.stream(exposed.split(",")).map(String::trim).toList())
                .as("Date is not exposed, so a cross-origin browser cannot read it")
                .anyMatch("Date"::equalsIgnoreCase);

        // A usable clock rather than a placeholder. RFC 1123 has one-second
        // resolution, which is far finer than the skew this exists to catch.
        Instant serverTime = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        assertThat(Duration.between(serverTime, Instant.now()).abs())
                .as("Date is not the current time")
                .isLessThan(Duration.ofMinutes(1));
    }
}
