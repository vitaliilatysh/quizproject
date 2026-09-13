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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies that authorization follows the server-side refresh-session state. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenRefreshRevocationTest {
    private static final int BLOCKED_STATUS = 2;
    private static final int ACTIVE_STATUS = 1;
    private static final int ADMIN_ROLE = 1;
    private static final int STUDENT_ROLE = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aBlockedAccountImmediatelyLosesAccessAndRefresh() throws Exception {
        Tokens tokens = login("apiuser", "203.0.113.11");
        mockMvc.perform(get("/api/v1/users/me").with(from("203.0.113.11"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET status_id = ? WHERE login = 'apiuser'", BLOCKED_STATUS);
        try {
            assertAccessAndRefreshAreUnauthorized(tokens, "203.0.113.11");
        } finally {
            jdbcTemplate.update("UPDATE users SET status_id = ? WHERE login = 'apiuser'", ACTIVE_STATUS);
        }
    }

    @Test
    void aDeletedAccountImmediatelyLosesAccessAndRefresh() throws Exception {
        Tokens tokens = login("orphan", "203.0.113.12");
        jdbcTemplate.update("DELETE FROM users WHERE login = 'orphan'");
        try {
            assertAccessAndRefreshAreUnauthorized(tokens, "203.0.113.12");
        } finally {
            jdbcTemplate.update("INSERT INTO users"
                    + " (id, login, password, first_name, last_name, register_date, status_id, role_id)"
                    + " VALUES (7, 'orphan', 'secret123', 'Orphan', 'User',"
                    + " TIMESTAMP '2025-01-07 09:00:00', ?, ?)", ACTIVE_STATUS, STUDENT_ROLE);
        }
    }

    @Test
    void aRotatedAccessTokenCarriesTheCurrentRole() throws Exception {
        Tokens tokens = login("admin", "203.0.113.13");
        mockMvc.perform(get("/api/v1/admin/status").with(from("203.0.113.13"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET role_id = ? WHERE login = 'admin'", STUDENT_ROLE);
        try {
            Tokens demoted = refresh(tokens.refreshToken(), "203.0.113.13");
            assertThat(demoted.accessToken()).isNotEqualTo(tokens.accessToken());
            assertThat(demoted.refreshToken()).isNotEqualTo(tokens.refreshToken());
            mockMvc.perform(get("/api/v1/admin/status").with(from("203.0.113.13"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(demoted.accessToken())))
                    .andExpect(status().isForbidden());
        } finally {
            jdbcTemplate.update("UPDATE users SET role_id = ? WHERE login = 'admin'", ADMIN_ROLE);
        }
    }

    @Test
    void anActiveSessionRotatesAndTheNewAccessTokenWorks() throws Exception {
        Tokens original = login("student", "203.0.113.14");
        Tokens rotated = refresh(original.refreshToken(), "203.0.113.14");

        assertThat(rotated.accessToken()).isNotEqualTo(original.accessToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(original.refreshToken());
        mockMvc.perform(get("/api/v1/results/me").with(from("203.0.113.14"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(rotated.accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    void aPasswordChangeRevokesTheWholeCurrentSession() throws Exception {
        Tokens tokens = login("rotator", "203.0.113.15");
        try {
            changePassword(tokens.accessToken(), "secret123", "rotated-secret-9", "203.0.113.15");
            assertAccessAndRefreshAreUnauthorized(tokens, "203.0.113.15");

            Tokens reissued = login("rotator", "rotated-secret-9", "203.0.113.16");
            Tokens rotated = refresh(reissued.refreshToken(), "203.0.113.16");
            assertThat(rotated.accessToken()).isNotEqualTo(reissued.accessToken());
        } finally {
            jdbcTemplate.update(
                    "UPDATE users SET password = 'secret123', credentials_changed_at = NULL"
                            + " WHERE login = 'rotator'");
        }
    }

    private void assertAccessAndRefreshAreUnauthorized(Tokens tokens, String address) throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(from(address))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").with(from(address))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
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

    private Tokens login(String username, String address) throws Exception {
        return login(username, "secret123", address);
    }

    private Tokens login(String username, String password, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(from(address))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                                .formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return tokens(result);
    }

    private Tokens refresh(String refreshToken, String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").with(from(address))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        return tokens(result);
    }

    private Tokens tokens(MvcResult result) throws Exception {
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Tokens(body.get("accessToken").asString(), body.get("refreshToken").asString());
    }

    private static String refreshBody(String token) {
        return "{\"refreshToken\":\"%s\"}".formatted(token);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
