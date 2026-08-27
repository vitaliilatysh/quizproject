package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicQuizCachingIntegrationTest {
    @LocalServerPort
    private int port;

    @Test
    void returnsPublicCacheHeadersAndHonorsConditionalRequests() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI quizzes = URI.create("http://127.0.0.1:" + port + "/api/v1/quizzes?page=0&size=1");
        HttpResponse<byte[]> initial = client.send(
                HttpRequest.newBuilder(quizzes).header("Accept-Encoding", "identity").build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(initial.statusCode()).isEqualTo(200);
        assertThat(initial.headers().firstValue("Cache-Control").orElseThrow())
                .contains("max-age=60", "must-revalidate", "public");
        String etag = initial.headers().firstValue("ETag").orElseThrow();
        assertThat(etag).startsWith("W/\"");

        HttpResponse<byte[]> unchanged = client.send(
                HttpRequest.newBuilder(quizzes)
                        .header("Accept-Encoding", "identity")
                        .header("If-None-Match", etag)
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(unchanged.statusCode()).isEqualTo(304);
        assertThat(unchanged.body()).isEmpty();
    }
}
