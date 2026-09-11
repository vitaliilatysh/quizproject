package ua.nure.latysh.quizzes.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import tools.jackson.databind.ObjectMapper;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a token survives, and what it must not.
 *
 * This API is a stateless resource server: the subject and the roles come from
 * the token's claims and nothing re-reads the account per request. That is a
 * deliberate trade and it costs at most one token lifetime — but only because
 * the one endpoint that extends a lifetime re-reads the account first.
 *
 * Before it did, the cost was unbounded. Blocking someone set a column and
 * changed nothing: their client refreshed on its own timer, each refresh minted
 * from the previous token's claims, and the account stayed usable for as long
 * as the tab was open. These tests are the reason that cannot come back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenRefreshRevocationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    private static final int BLOCKED_STATUS = 2;
    private static final int ACTIVE_STATUS = 1;
    private static final int ADMIN_ROLE = 1;
    private static final int STUDENT_ROLE = 2;

    @Test
    void aBlockedAccountCannotRefreshTheTokenItAlreadyHolds() throws Exception {
        String token = login("apiuser", "203.0.113.11");
        // The token still works, which is the state this is about: the block
        // lands while a session is open, not before it starts.
        mockMvc.perform(get("/api/v1/users/me").with(from("203.0.113.11"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET status_id = ? WHERE login = 'apiuser'", BLOCKED_STATUS);
        try {
            mockMvc.perform(post("/api/v1/auth/refresh").with(from("203.0.113.11"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbcTemplate.update("UPDATE users SET status_id = ? WHERE login = 'apiuser'", ACTIVE_STATUS);
        }
    }

    @Test
    void anAccountThatNoLongerExistsCannotRefresh() throws Exception {
        String token = login("orphan", "203.0.113.12");
        jdbcTemplate.update("DELETE FROM users WHERE login = 'orphan'");
        try {
            mockMvc.perform(post("/api/v1/auth/refresh").with(from("203.0.113.12"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized());
        } finally {
            // Columns named rather than positional: a VALUES list that matches the
            // table by position breaks silently the next time one is added, which
            // is how adding credentials_changed_at took this test out.
            jdbcTemplate.update("INSERT INTO users"
                    + " (id, login, password, first_name, last_name, register_date, status_id, role_id)"
                    + " VALUES (7, 'orphan', 'secret123', 'Orphan', 'User',"
                    + " TIMESTAMP '2025-01-07 09:00:00', ?, ?)", ACTIVE_STATUS, STUDENT_ROLE);
        }
    }

    // The other direction of the same read: a refreshed token carries the roles
    // the database holds now, not the ones the old token was minted with. An
    // administrator who has been demoted loses the panel on their next refresh
    // rather than never.
    @Test
    void aRefreshedTokenCarriesTheRoleTheAccountHasNow() throws Exception {
        String token = login("admin", "203.0.113.13");
        mockMvc.perform(get("/api/v1/admin/status").with(from("203.0.113.13"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET role_id = ? WHERE login = 'admin'", STUDENT_ROLE);
        try {
            String demoted = refresh(token, "203.0.113.13");
            assertThat(demoted).isNotEqualTo(token);
            mockMvc.perform(get("/api/v1/admin/status").with(from("203.0.113.13"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(demoted)))
                    .andExpect(status().isForbidden());
        } finally {
            jdbcTemplate.update("UPDATE users SET role_id = ? WHERE login = 'admin'", ADMIN_ROLE);
        }
    }

    @Test
    void anActiveAccountStillRefreshesAndTheNewTokenWorks() throws Exception {
        String token = login("student", "203.0.113.14");
        String refreshed = refresh(token, "203.0.113.14");

        assertThat(refreshed).isNotEqualTo(token);
        mockMvc.perform(get("/api/v1/results/me").with(from("203.0.113.14"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(refreshed)))
                .andExpect(status().isOk());
    }

    @Test
    void aPasswordChangeStopsATokenIssuedBeforeItFromRefreshing() throws Exception {
        String token = login("rotator", "203.0.113.15");
        try {
            changePassword(token, "secret123", "rotated-secret-9", "203.0.113.15");

            assertThat(credentialsChangedAt("rotator"))
                    .as("the change has to be recorded, or there is nothing to compare a token against")
                    .isNotNull();

            mockMvc.perform(post("/api/v1/auth/refresh").with(from("203.0.113.15"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized());
        } finally {
            restoreRotator();
        }
    }

    /**
     * The bound this buys, and the one it does not.
     *
     * <p>A token already issued keeps working until it expires — this is a
     * stateless resource server and no ordinary request re-reads the account.
     * What changes is that the session cannot be extended past that, so the
     * exposure is one token lifetime rather than however long a thief cares to
     * keep refreshing. Asserted rather than claimed, because the difference
     * between the two is the whole point of the endpoint doing this at all.
     */
    @Test
    void theOlderTokenStillReadsUntilItExpires() throws Exception {
        String token = login("rotator", "203.0.113.16");
        try {
            changePassword(token, "secret123", "rotated-secret-9", "203.0.113.16");

            mockMvc.perform(get("/api/v1/users/me").with(from("203.0.113.16"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
        } finally {
            restoreRotator();
        }
    }

    // The other direction, and the one that keeps the test above from passing
    // for the wrong reason: a check that refused every refresh would satisfy it
    // too. Signing in again is the honest way to get a token from after the
    // change, and it is available here only because the comparison is on the
    // stamp rather than on iat — a login in the same second as the change reads
    // as older than it, and used to have to be faked with a backdated column.
    @Test
    void aTokenIssuedAfterThePasswordChangedStillRefreshes() throws Exception {
        String token = login("rotator", "203.0.113.17");
        try {
            changePassword(token, "secret123", "rotated-secret-9", "203.0.113.17");

            String reissued = login("rotator", "rotated-secret-9", "203.0.113.17");
            String refreshed = refresh(reissued, "203.0.113.17");

            assertThat(refreshed).isNotEqualTo(reissued);
        } finally {
            restoreRotator();
        }
    }

    /**
     * A second change invalidates the token the first one handed out.
     *
     * <p>The property the equality comparison buys and an ordering comparison
     * did not: what a token survives depends on the stamp it quotes, not on when
     * it happens to be dated. Both changes here land within a second or two of
     * each other, which is the case that defeated comparing against iat.
     */
    @Test
    void eachPasswordChangeInvalidatesTheTokenTheLastOneLeft() throws Exception {
        String token = login("rotator", "203.0.113.18");
        try {
            changePassword(token, "secret123", "rotated-secret-9", "203.0.113.18");
            String afterFirst = login("rotator", "rotated-secret-9", "203.0.113.18");

            changePassword(afterFirst, "rotated-secret-9", "rotated-secret-10", "203.0.113.18");

            mockMvc.perform(post("/api/v1/auth/refresh").with(from("203.0.113.18"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(afterFirst)))
                    .andExpect(status().isUnauthorized());
        } finally {
            restoreRotator();
        }
    }

    /**
     * A token minted before the claim existed still refreshes.
     *
     * <p>The rolling-deployment case, and the reason a missing claim reads as
     * "no change recorded" rather than as a mismatch: every token in flight when
     * this ships lacks {@code cca}, and refusing them would sign out everyone
     * with a tab open. It cannot be produced through the API — every token this
     * build issues carries the claim — so it is minted here the way the old code
     * minted it.
     */
    @Test
    void aTokenFromBeforeTheClaimExistedStillRefreshes() throws Exception {
        Instant issuedAt = Instant.now();
        JwtClaimsSet withoutTheClaim = JwtClaimsSet.builder()
                .issuer(securityProperties.issuer())
                .subject("rotator")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(securityProperties.tokenTtl()))
                .claim("roles", List.of("ROLE_USER"))
                .build();
        String legacyToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), withoutTheClaim)).getTokenValue();

        refresh(legacyToken, "203.0.113.19");
    }

    private void changePassword(String token, String current, String replacement, String address)
            throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password").with(from(address))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}"
                                .formatted(current, replacement)))
                .andExpect(status().isNoContent());
    }

    private Instant credentialsChangedAt(String username) {
        Timestamp recorded = jdbcTemplate.queryForObject(
                "SELECT credentials_changed_at FROM users WHERE login = ?", Timestamp.class, username);
        return recorded == null ? null : recorded.toInstant();
    }

    private void restoreRotator() {
        jdbcTemplate.update(
                "UPDATE users SET password = 'secret123', credentials_changed_at = NULL WHERE login = 'rotator'");
    }

    private String login(String username, String address) throws Exception {
        return login(username, "secret123", address);
    }

    private String login(String username, String password, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(from(address))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                                .formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return accessToken(result);
    }

    private String refresh(String token, String address) throws Exception {
        return accessToken(mockMvc.perform(post("/api/v1/auth/refresh").with(from(address))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // Its own client address per test. The rate limit is keyed on one, and
    // MockMvc otherwise defaults every request to 127.0.0.1 — one bucket of a
    // hundred a minute shared by every test in the run.
    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
