package ua.nure.latysh.quizzes.security;

import org.junit.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class SecurityCoverageTest {

    @Test
    public void passwordHasherCoversVersionedLegacyMalformedAndCryptoFailurePaths() {
        assertNotNull(new PasswordHasher());
        SecureRandom random = mock(SecureRandom.class);
        PasswordHasher hasher = new PasswordHasher(random, "PBKDF2WithHmacSHA256", 100);

        String encoded = hasher.hash("strong-password");
        assertTrue(hasher.isEncoded(encoded));
        assertTrue(hasher.matches("strong-password", encoded));
        assertFalse(hasher.matches("wrong-password", encoded));
        assertFalse(hasher.matches(null, encoded));
        assertFalse(hasher.matches("strong-password", "malformed"));
        assertFalse(hasher.matches("strong-password", "pbkdf2-sha256$short"));
        assertFalse(hasher.matches("strong-password", "pbkdf2-sha256$bad$%%%$%%%"));
        assertTrue(hasher.matchesLegacy("legacy", "legacy"));
        assertFalse(hasher.matchesLegacy("legacy", null));
        assertFalse(hasher.isEncoded(null));

        try {
            new PasswordHasher(random, "missing-algorithm", 100).hash("password");
            fail("Expected unsupported crypto algorithm to fail closed");
        } catch (IllegalStateException expected) {
            assertNotNull(expected.getCause());
        }
    }

    @Test
    public void loginLimiterCoversWindowBlockingExpirationAndReset() {
        assertNotNull(new LoginAttemptLimiter());
        MutableClock clock = new MutableClock(Instant.parse("2026-08-12T08:00:00Z"));
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

        assertFalse(limiter.isBlocked("client"));
        for (int index = 0; index < 5; index++) {
            limiter.recordFailure("client");
        }
        assertTrue(limiter.isBlocked("client"));

        limiter.recordSuccess("client");
        assertFalse(limiter.isBlocked("client"));

        limiter.recordFailure("client");
        clock.advanceSeconds(16 * 60);
        assertFalse(limiter.isBlocked("client"));
        limiter.recordFailure("client");
        clock.advanceSeconds(16 * 60);
        limiter.recordFailure("client");
        assertFalse(limiter.isBlocked("client"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
