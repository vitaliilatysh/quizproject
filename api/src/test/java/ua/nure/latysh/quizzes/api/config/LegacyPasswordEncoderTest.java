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

    /**
     * The case the upgrade signal used to miss.
     *
     * <p>matches() derives with the count written into the stored value, which
     * is what lets a hash from a cheaper setting still verify. That leniency is
     * only safe if such a row is then rewritten — and it was not, because it
     * carried the prefix and so read as current.
     */
    @Test
    void asksForAnUpgradeOfAHashWrittenUnderACheaperSetting() {
        LegacyPasswordEncoder cheap = new LegacyPasswordEncoder(new SecureRandom(),
                "PBKDF2WithHmacSHA256", 1_000);
        LegacyPasswordEncoder current = new LegacyPasswordEncoder(new SecureRandom(),
                "PBKDF2WithHmacSHA256", 10_000);
        String cheapHash = cheap.encode("secret123");

        assertTrue(current.matches("secret123", cheapHash),
                "a hash from a cheaper setting stopped verifying, which is the leniency being kept");
        assertTrue(current.upgradeEncoding(cheapHash),
                "a hash at a thousand iterations was called current under ten thousand");
        assertFalse(current.upgradeEncoding(current.encode("secret123")),
                "a hash at the current cost was rewritten for no reason");
        assertFalse(cheap.upgradeEncoding(current.encode("secret123")),
                "a hash stronger than the configured cost was treated as weaker");
    }

    /**
     * A row whose iteration count cannot be read, or is not a cost at all.
     * PBEKeySpec throws on a count at or below zero, and that exception would
     * otherwise leave matches() reporting a wrong password for a broken row.
     */
    @Test
    void refusesAStoredCostThatIsNotOne() {
        LegacyPasswordEncoder encoder = new LegacyPasswordEncoder(new SecureRandom(),
                "PBKDF2WithHmacSHA256", 1_000);

        for (String broken : new String[]{"pbkdf2-sha256$0$c2FsdA$aGFzaA", "pbkdf2-sha256$-1$c2FsdA$aGFzaA"}) {
            assertFalse(encoder.matches("secret123", broken), broken + " was accepted as a cost");
            assertTrue(encoder.upgradeEncoding(broken), broken + " was called current");
        }
        assertTrue(encoder.upgradeEncoding("pbkdf2-sha256$x$c2FsdA$aGFzaA"),
                "an unreadable count was called current");
    }

    @Test
    void reportsUnavailableHashingAlgorithm() {
        LegacyPasswordEncoder encoder = new LegacyPasswordEncoder(
                new SecureRandom(), "missing-password-algorithm", 1);

        assertThrows(IllegalStateException.class, () -> encoder.encode("secret123"));
    }
}
