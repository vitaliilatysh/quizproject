package ua.nure.latysh.quizzes.api;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import ua.nure.latysh.quizzes.api.account.AccountService;
import ua.nure.latysh.quizzes.api.account.ChangePasswordRequest;
import ua.nure.latysh.quizzes.api.auth.RegisterRequest;
import ua.nure.latysh.quizzes.api.auth.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {"quiz.security.rate-limit.backend=memory"})
@AutoConfigureMockMvc
class BackendAuditProbeIntegrationTest {
    @Container
    @ServiceConnection
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("tests_db").withUsername("quiz").withPassword("quiz-password");
    @Autowired private AccountService accounts;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private TokenService tokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    @Test
    void loginDateUpdateCanRestoreTheOldPasswordAfterConcurrentPasswordChange() throws Exception {
        accounts.register(new RegisterRequest("auditlost", "Audit", "User", "OriginalPass1"));
        var transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var pool = Executors.newSingleThreadExecutor();
        try {
            transaction.executeWithoutResult(state -> {
                accounts.recordLogin("auditlost");
                try {
                    pool.submit(() -> accounts.changePassword("auditlost",
                            new ChangePasswordRequest("OriginalPass1", "ReplacementPass1")))
                            .get(30, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
        } finally {
            pool.shutdown();
        }
        String password = jdbc.queryForObject(
                "SELECT password FROM users WHERE login='auditlost'", String.class);
        assertThat(encoder.matches("OriginalPass1", password)).isTrue();
        assertThat(encoder.matches("ReplacementPass1", password)).isFalse();
        System.out.println("AUDIT_CONFIRMED: MySQL concurrent recordLogin restores original password");
    }

    @Test
    void credentialsCheckedBeforePasswordChangeCanCreateANewLiveSessionAfterIt() throws Exception {
        accounts.register(new RegisterRequest("auditrace", "Audit", "User", "OriginalPass1"));
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("auditrace", "OriginalPass1"));
        accounts.changePassword("auditrace",
                new ChangePasswordRequest("OriginalPass1", "ReplacementPass1"));
        accounts.recordLogin("auditrace");
        var response = tokens.issue(authentication);
        mvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + response.accessToken()))
                .andExpect(status().isOk());
        String password = jdbc.queryForObject(
                "SELECT password FROM users WHERE login='auditrace'", String.class);
        assertThat(encoder.matches("ReplacementPass1", password)).isTrue();
        System.out.println("AUDIT_CONFIRMED: stale password authentication creates live post-change session");
    }
}
