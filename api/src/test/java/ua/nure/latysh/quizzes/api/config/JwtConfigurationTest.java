package ua.nure.latysh.quizzes.api.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigurationTest {
    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    void createsA256BitOrStrongerHmacKey() {
        var key = configuration.jwtSecretKey(properties(
                "cDctdGVzdC1zZWNyZXQtbXVzdC1iZS1hdC1sZWFzdC0zMi1ieXRlcw=="));

        assertEquals("HmacSHA256", key.getAlgorithm());
        assertEquals(40, key.getEncoded().length);
    }

    @Test
    void rejectsMalformedAndShortSecrets() {
        assertThrows(IllegalStateException.class,
                () -> configuration.jwtSecretKey(properties("not-base64!")));
        assertThrows(IllegalStateException.class,
                () -> configuration.jwtSecretKey(properties("c2hvcnQ=")));
    }

    private static SecurityProperties properties(String secret) {
        return new SecurityProperties(secret, "quiz-api", Duration.ofMinutes(15), List.of("https://example.test"),
                new SecurityProperties.RateLimitProperties(100, 3, Duration.ofMinutes(1), 100));
    }
}
