package ua.nure.latysh.quizzes.api.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenHasherTest {
    @Test
    void hashesAndComparesRefreshTokens() {
        RefreshTokenHasher hasher = new RefreshTokenHasher();
        String hash = hasher.hash("refresh-token");

        assertThat(hash).hasSize(64);
        assertThat(hasher.matches("refresh-token", hash)).isTrue();
        assertThat(hasher.matches("different-token", hash)).isFalse();
    }

    @Test
    void reportsAnUnavailableHashAlgorithm() {
        RefreshTokenHasher hasher = new RefreshTokenHasher("missing-algorithm");

        assertThatThrownBy(() -> hasher.hash("refresh-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Refresh token hashing is unavailable");
    }
}
