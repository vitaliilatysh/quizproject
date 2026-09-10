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
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
            jdbcTemplate.update("INSERT INTO users VALUES (7, 'orphan', 'secret123', 'Orphan', 'User',"
                    + " TIMESTAMP '2025-01-07 09:00:00', NULL, ?, ?)", ACTIVE_STATUS, STUDENT_ROLE);
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

    private String login(String username, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(from(address))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"secret123\"}".formatted(username)))
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
