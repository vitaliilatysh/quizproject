package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API contract for signing in, refresh-token rotation, registration, the password change and what revokes a session. */
class AuthenticationContractTest extends ApiContractTestBase {
    @Test
    void exchangesCredentialsForBearerTokenAndReadsCurrentUserResults() throws Exception {
        String studentToken = login("student", "secret123", "192.0.2.10");

        mockMvc.perform(get("/api/v1/results/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));

        mockMvc.perform(get("/api/v1/results/me").with(httpBasic("student", "secret123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptId").value(1))
                .andExpect(jsonPath("$[0].quizId").value(1))
                .andExpect(jsonPath("$[0].quizName").value("Java syntax"))
                .andExpect(jsonPath("$[0].score").value(80))
                .andExpect(jsonPath("$[0].completedAt").value("2026-08-12T10:15:30Z"));

        String emptyToken = login("empty", "secret123", "192.0.2.11");
        mockMvc.perform(get("/api/v1/results/me").header(HttpHeaders.AUTHORIZATION, bearer(emptyToken)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void rotatesRefreshTokensAndRejectsReplay() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.12")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.12"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.12"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("invalid")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
        mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.12"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("00000000-0000-0000-0000-000000000000.unknown")))
                .andExpect(status().isUnauthorized());

        Tokens original = loginTokens("student", "secret123", "192.0.2.12");
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.12"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(original.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        var refreshedBody = objectMapper.readTree(refreshed.getResponse().getContentAsString());
        String refreshedAccessToken = refreshedBody.get("accessToken").asString();
        String rotatedRefreshToken = refreshedBody.get("refreshToken").asString();
        Assertions.assertNotEquals(original.accessToken(), refreshedAccessToken);
        Assertions.assertNotEquals(original.refreshToken(), rotatedRefreshToken);

        mockMvc.perform(get("/api/v1/results/me").with(from("192.0.2.12"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(refreshedAccessToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.12"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(original.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/results/me").with(from("192.0.2.12"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(refreshedAccessToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.12"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(rotatedRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logsOutAndRevokesBothTokensInTheCurrentSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(from("192.0.2.15")))
                .andExpect(status().isUnauthorized());
        Tokens tokens = loginTokens("student", "secret123", "192.0.2.15");

        mockMvc.perform(post("/api/v1/auth/logout").with(from("192.0.2.15"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/results/me").with(from("192.0.2.15"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.15"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidBlockedAndMalformedLoginRequests() throws Exception {
        performLogin("missing", "secret123", "192.0.2.20")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        performLogin("blocked", "secret123", "192.0.2.21")
                .andExpect(status().isUnauthorized());

        performLogin("student", "wrong-password", "192.0.2.22")
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.23");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void registersProfilesAndChangesTheCurrentUsersPassword() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"short","firstName":"","lastName":"User","password":"spaces are bad"}
                                """)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.70");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"student","firstName":"Existing","lastName":"User","password":"secret123"}
                                """)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.71");
                            return request;
                        }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username is already registered"));

        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"p10user","firstName":"Vitalii","lastName":"Latysh","password":"initial123"}
                                """)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.72");
                            return request;
                        }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        var registeredBody = objectMapper.readTree(registered.getResponse().getContentAsString());
        String token = registeredBody.get("accessToken").asString();
        String refreshToken = registeredBody.get("refreshToken").asString();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("p10user"))
                .andExpect(jsonPath("$.firstName").value("Vitalii"))
                .andExpect(jsonPath("$.lastName").value("Latysh"))
                .andExpect(jsonPath("$.role").value("student"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.registeredAt").exists())
                .andExpect(jsonPath("$.lastLoginAt").exists());

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"updated123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"initial123\",\"newPassword\":\"initial123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("New password must differ from the current password"));
        mockMvc.perform(put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"initial123\",\"newPassword\":\"updated123\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").with(from("192.0.2.72"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").with(from("192.0.2.72"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());

        performLogin("p10user", "initial123", "192.0.2.73")
                .andExpect(status().isUnauthorized());
        performLogin("p10user", "updated123", "192.0.2.74")
                .andExpect(status().isOk());
    }

    @Test
    void rejectsATokenWhoseUserWasRemovedAfterLogin() throws Exception {
        String token = login("orphan", "secret123", "192.0.2.52");
        jdbcTemplate.update("DELETE FROM users WHERE id = 7");
        try {
            mockMvc.perform(get("/api/v1/users/me").with(from("192.0.2.52"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(put("/api/v1/users/me/password").with(from("192.0.2.52"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"secret123\",\"newPassword\":\"updated123\"}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/quizzes/1/attempts").with(from("192.0.2.52"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isUnauthorized());
        } finally {
            // Columns named rather than positional: a VALUES list matched to the
            // table by position breaks silently the next time a column is added.
            jdbcTemplate.update("""
                    INSERT INTO users
                      (id, login, password, first_name, last_name, register_date, status_id, role_id)
                    VALUES (7, 'orphan', 'secret123', 'Orphan', 'User',
                            TIMESTAMP '2025-01-07 09:00:00', 1, 2)
                    """);
        }
    }

    @Test
    void rateLimitsRepeatedLoginAttemptsByRemoteAddress() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            performLogin("student", "wrong-password", "192.0.2.40")
                    .andExpect(status().isUnauthorized());
        }
        performLogin("student", "secret123", "192.0.2.40")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"));
    }

    @Test
    void rehashesLegacyPlainTextPasswordsOnSuccessfulLogin() throws Exception {
        // The legacy schema stored passwords in plain text (VARCHAR(15) could
        // hold nothing else) and V2 only widened the column, so migrated rows
        // stay readable until something re-encodes them. This fixture is in
        // exactly that state, which is why every other login test works.
        jdbcTemplate.update("""
                INSERT INTO users
                  (id, login, password, first_name, last_name, register_date, status_id, role_id)
                VALUES (50, 'legacyuser', 'secret123', 'Legacy', 'User',
                        TIMESTAMP '2025-01-08 09:00:00', 1, 2)
                """);
        try {
            assertThat(storedPassword("legacyuser")).isEqualTo("secret123");

            login("legacyuser", "secret123", "192.0.2.90");

            String upgraded = storedPassword("legacyuser");
            assertThat(upgraded).startsWith("pbkdf2-sha256$");
            assertThat(upgraded).doesNotContain("secret123");

            // The account still works, and a second login must not re-encode
            // what is already in the current format.
            login("legacyuser", "secret123", "192.0.2.91");
            assertThat(storedPassword("legacyuser")).isEqualTo(upgraded);

            performLogin("legacyuser", "wrong-password", "192.0.2.92")
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE id = 50");
        }
    }

    @Test
    void blockingAnAdministratorImmediatelyRevokesItsSession() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO users (id, login, password, first_name, last_name,
                                   register_date, login_date, status_id, role_id)
                VALUES (200, 'admin2', 'secret123', 'Second', 'Admin',
                        TIMESTAMP '2025-01-08 09:00:00', NULL, 1, 1)
                """);
        try {
            String adminToken = login("admin", "secret123", "192.0.2.94");
            String secondAdminToken = login("admin2", "secret123", "192.0.2.95");

            // Two administrators are active, so blocking one leaves cover.
            mockMvc.perform(patch("/api/v1/admin/users/200/status")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"blocked\"}")
                            .with(from("192.0.2.96")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("blocked"));

            // A blocked account cannot use an access token that was already
            // issued. The database-backed session check closes that window.
            mockMvc.perform(patch("/api/v1/admin/users/5/status")
                            .header(HttpHeaders.AUTHORIZATION, bearer(secondAdminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"blocked\"}")
                            .with(from("192.0.2.96")))
                    .andExpect(status().isUnauthorized());

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status_id FROM users WHERE id = 5", Integer.class))
                    .as("the remaining administrator is untouched")
                    .isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE id = 200");
        }
    }
}
