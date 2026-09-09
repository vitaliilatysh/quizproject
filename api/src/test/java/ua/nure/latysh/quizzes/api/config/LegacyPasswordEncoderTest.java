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

    // The upgrade signal is what drains the plain-text rows the legacy schema
    // left behind: Spring Security re-encodes a password on a successful login
    // only when this says the stored value is not in the current format.
    @Test
    void asksForAnUpgradeOnlyForAPasswordThatIsNotAlreadyEncoded() {
        LegacyPasswordEncoder encoder = new LegacyPasswordEncoder(new SecureRandom(),
                "PBKDF2WithHmacSHA256", 1_000);

        assertTrue(encoder.upgradeEncoding("legacy"), "a plain-text row was left in plain text");
        assertTrue(encoder.upgradeEncoding("pbkdf2-sha256"), "a prefix without its separator is not this format");
        assertFalse(encoder.upgradeEncoding(encoder.encode("secret123")));
        // A user row with no password at all: nothing to upgrade, and asking for
        // one would re-encode an empty credential into a valid-looking hash.
        assertFalse(encoder.upgradeEncoding(null));
    }

    @Test
    void reportsUnavailableHashingAlgorithm() {
        LegacyPasswordEncoder encoder = new LegacyPasswordEncoder(
                new SecureRandom(), "missing-password-algorithm", 1);

        assertThrows(IllegalStateException.class, () -> encoder.encode("secret123"));
    }
}
