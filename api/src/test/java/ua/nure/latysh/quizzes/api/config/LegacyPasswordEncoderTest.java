package ua.nure.latysh.quizzes.api.config;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyPasswordEncoderTest {
    @Test
    void supportsEncodedAndLegacyPasswords() {
        LegacyPasswordEncoder encoder = new LegacyPasswordEncoder(new SecureRandom(),
                "PBKDF2WithHmacSHA256", 1_000);
        String encoded = encoder.encode("secret123");

        assertTrue(encoded.startsWith("pbkdf2-sha256$1000$"));
        assertTrue(encoder.matches("secret123", encoded));
        assertFalse(encoder.matches("wrong", encoded));
        assertTrue(encoder.matches("legacy", "legacy"));
        assertFalse(encoder.matches("wrong", "legacy"));
        assertFalse(encoder.matches(null, encoded));
        assertFalse(encoder.matches("secret123", null));
        assertFalse(encoder.matches("secret123", "pbkdf2-sha256$bad"));
        assertFalse(encoder.matches("secret123", "pbkdf2-sha256$x$salt$hash"));
    }

    @Test
    void reportsUnavailableHashingAlgorithm() {
        LegacyPasswordEncoder encoder = new LegacyPasswordEncoder(
                new SecureRandom(), "missing-password-algorithm", 1);

        assertThrows(IllegalStateException.class, () -> encoder.encode("secret123"));
    }
}
