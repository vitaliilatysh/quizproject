package ua.nure.latysh.quizzes.api.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RefreshSessionTest {
    @Test
    void exposesJpaConstructorAndMutableSessionState() {
        assertThat(new RefreshSession()).isNotNull();

        UserAccount user = mock(UserAccount.class);
        Instant createdAt = Instant.parse("2026-09-13T06:00:00Z");
        RefreshSession session = new RefreshSession(
                "session", user, "hash", createdAt, createdAt.plusSeconds(60));

        assertThat(session.getUser()).isSameAs(user);
        assertThat(session.getTokenHash()).isEqualTo("hash");
        assertThat(session.getExpiresAt()).isEqualTo(createdAt.plusSeconds(60));
        assertThat(session.getRevokedAt()).isNull();

        session.rotate("rotated", createdAt.plusSeconds(1), createdAt.plusSeconds(120));
        session.revoke(createdAt.plusSeconds(2));

        assertThat(session.getTokenHash()).isEqualTo("rotated");
        assertThat(session.getExpiresAt()).isEqualTo(createdAt.plusSeconds(120));
        assertThat(session.getRevokedAt()).isEqualTo(createdAt.plusSeconds(2));
    }
}
