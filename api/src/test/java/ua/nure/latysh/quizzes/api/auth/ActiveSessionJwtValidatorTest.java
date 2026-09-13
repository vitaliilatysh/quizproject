package ua.nure.latysh.quizzes.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import ua.nure.latysh.quizzes.api.domain.RefreshSessionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveSessionJwtValidatorTest {
    private static final Instant NOW = Instant.parse("2026-09-13T06:00:00Z");
    private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    private final ActiveSessionJwtValidator validator = new ActiveSessionJwtValidator(
            sessions, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsAnActiveSession() {
        when(sessions.countActive("session-id", "student", NOW)).thenReturn(1L);

        assertThat(validator.validate(jwt("student", "session-id")).hasErrors()).isFalse();
    }

    @Test
    void rejectsMissingAndInactiveSessions() {
        assertThat(validator.validate(jwt("student", null)).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("", "session-id")).hasErrors()).isTrue();
        when(sessions.countActive("session-id", "student", NOW)).thenReturn(0L);
        assertThat(validator.validate(jwt("student", "session-id")).hasErrors()).isTrue();
    }

    private static Jwt jwt(String subject, String sessionId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "HS256").subject(subject);
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }
        return builder.build();
    }
}
