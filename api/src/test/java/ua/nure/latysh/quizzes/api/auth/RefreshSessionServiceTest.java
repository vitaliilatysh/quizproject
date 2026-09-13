package ua.nure.latysh.quizzes.api.auth;

import org.junit.jupiter.api.Test;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;
import ua.nure.latysh.quizzes.api.domain.RefreshSession;
import ua.nure.latysh.quizzes.api.domain.RefreshSessionRepository;
import ua.nure.latysh.quizzes.api.domain.Status;
import ua.nure.latysh.quizzes.api.domain.UserAccount;
import ua.nure.latysh.quizzes.api.domain.UserRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ua.nure.latysh.quizzes.api.config.SecurityProperties.RateLimitProperties.Backend.MEMORY;

class RefreshSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-13T06:00:00Z");
    private static final String SESSION_ID = "00000000-0000-0000-0000-000000000001";

    private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenHasher hasher = mock(RefreshTokenHasher.class);
    private final SecureRandom random = mock(SecureRandom.class);
    private final RefreshSessionService service = new RefreshSessionService(
            sessions,
            users,
            hasher,
            properties(),
            random,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsAndRotatesARefreshSession() {
        UserAccount user = activeUser();
        when(users.findByLogin("student")).thenReturn(Optional.of(user));
        when(hasher.hash(any())).thenAnswer(invocation -> invocation.getArgument(0) + "-hash");

        RefreshSessionService.RefreshGrant created = service.create("student");

        assertThat(created.username()).isEqualTo("student");
        assertThat(created.refreshToken()).startsWith(created.sessionId() + ".");
        verify(sessions).save(any(RefreshSession.class));

        String originalToken = SESSION_ID + ".original";
        RefreshSession session = new RefreshSession(
                SESSION_ID, user, "original-hash", NOW.minusSeconds(60), NOW.plusSeconds(60));
        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(hasher.matches(originalToken, "original-hash")).thenReturn(true);

        RefreshSessionService.RefreshGrant rotated = service.rotate(originalToken);

        assertThat(rotated.username()).isEqualTo("student");
        assertThat(rotated.sessionId()).isEqualTo(SESSION_ID);
        assertThat(rotated.refreshToken()).startsWith(SESSION_ID + ".").isNotEqualTo(originalToken);
        assertThat(session.getTokenHash()).isEqualTo(rotated.refreshToken() + "-hash");
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void rejectsMissingUsersAndMalformedOrUnknownTokens() {
        when(users.findByLogin("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create("missing"))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> service.rotate("invalid"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate(".secret"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate(SESSION_ID + "."))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate("not-a-uuid.secret"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rotate(SESSION_ID + ".unknown"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rejectsRevokedExpiredInactiveAndReusedTokens() {
        UserAccount user = activeUser();
        RefreshSession revoked = session(user, NOW.plusSeconds(60));
        revoked.revoke(NOW.minusSeconds(1));
        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(revoked));
        assertThatThrownBy(() -> service.rotate(SESSION_ID + ".revoked"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        RefreshSession expired = session(user, NOW.minusSeconds(1));
        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.rotate(SESSION_ID + ".expired"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(expired.getRevokedAt()).isEqualTo(NOW);

        UserAccount inactiveUser = activeUser();
        when(inactiveUser.getStatus().getName()).thenReturn("blocked");
        RefreshSession inactive = session(inactiveUser, NOW.plusSeconds(60));
        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.rotate(SESSION_ID + ".inactive"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(inactive.getRevokedAt()).isEqualTo(NOW);

        RefreshSession reused = session(user, NOW.plusSeconds(60));
        when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(reused));
        when(hasher.matches(SESSION_ID + ".reused", "hash")).thenReturn(false);
        assertThatThrownBy(() -> service.rotate(SESSION_ID + ".reused"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(reused.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revokesOneOrEverySession() {
        service.revoke(SESSION_ID, "student");
        service.revokeAll("student");

        verify(sessions).revoke(SESSION_ID, "student", NOW);
        verify(sessions).revokeAll("student", NOW);
    }

    private static RefreshSession session(UserAccount user, Instant expiresAt) {
        return new RefreshSession(SESSION_ID, user, "hash", NOW.minusSeconds(60), expiresAt);
    }

    private static UserAccount activeUser() {
        UserAccount user = mock(UserAccount.class);
        Status status = mock(Status.class);
        when(user.getLogin()).thenReturn("student");
        when(user.getStatus()).thenReturn(status);
        when(status.getName()).thenReturn("active");
        return user;
    }

    private static SecurityProperties properties() {
        return new SecurityProperties(
                "secret",
                "issuer",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                List.of("https://example.test"),
                new SecurityProperties.RateLimitProperties(
                        MEMORY, 100, 3, Duration.ofMinutes(1), 100, List.of("127.0.0.1/32")));
    }
}
