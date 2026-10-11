package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class ResultSummaryContractTest extends ApiContractTestBase {
    @Test
    void summaryIsAuthenticatedAndScopedToTheCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/results/me/summary")).andExpect(status().isUnauthorized());
        String token = login("empty", "secret123", "192.0.2.190");
        mockMvc.perform(get("/api/v1/results/me/summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempts").value(0))
                .andExpect(jsonPath("$.averageScore").value(0.0))
                .andExpect(jsonPath("$.bestScore").value(0));
    }

    @Test
    void summaryIncludesAllCompletedResultsNotJustTheRequestedPage() throws Exception {
        String token = login("empty", "secret123", "192.0.2.191");
        for (int index = 0; index < 3; index++) {
            jdbcTemplate.update("""
                    INSERT INTO attempts (score, start_time, expires_at, end_time, completed, quiz_id, user_id)
                    VALUES (?, '2026-08-12 10:10:30', '2026-08-12 10:15:30',
                            '2026-08-12 10:14:30', TRUE, 1, 3)
                    """, 40 + index * 30);
        }
        // Incomplete attempts must not affect count, average or maximum.
        jdbcTemplate.update("""
                INSERT INTO attempts (score, start_time, expires_at, completed, quiz_id, user_id)
                VALUES (0, '2026-08-12 10:10:30', '2026-08-12 10:15:30', FALSE, 1, 3)
                """);
        mockMvc.perform(get("/api/v1/results/me").param("page", "1").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/results/me/summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempts").value(3))
                .andExpect(jsonPath("$.averageScore").value(70.0))
                .andExpect(jsonPath("$.bestScore").value(100));
    }
}
