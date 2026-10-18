package ua.nure.latysh.quizzes.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;
import ua.nure.latysh.quizzes.api.account.AccountService;
import ua.nure.latysh.quizzes.api.account.ChangePasswordRequest;
import ua.nure.latysh.quizzes.api.admin.UserAdminService;
import ua.nure.latysh.quizzes.api.domain.UserRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "quiz.security.rate-limit.backend=memory",
        "quiz.security.jwt-secret=cXVpei1wYXNzd29yZC1jb25jdXJyZW5jeS10ZXN0LXNlY3JldA==",
        "quiz.security.allowed-origins=https://app.example.test"
})
@AutoConfigureMockMvc
class PasswordConcurrencyIntegrationTest {
    private static final String ORIGINAL = "OriginalPass1";
    private static final String REPLACEMENT = "ReplacementPass1";

    @Container
    @ServiceConnection
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("tests_db").withUsername("quiz").withPassword("quiz-password");

    @Autowired private AccountService accounts;
    @Autowired private UserRepository users;
    @Autowired private UserAdminService administrators;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private TokenService tokens;
    @MockitoSpyBean private PasswordEncoder encoder;

    @Test
    void recordingLoginDoesNotRestoreAStalePassword() throws Exception {
        register("datelogin");
        var transaction = transaction();
        var pool = Executors.newSingleThreadExecutor();
        try {
            transaction.executeWithoutResult(state -> {
                users.findByLogin("datelogin").orElseThrow();
                try {
                    pool.submit(() -> changePassword("datelogin")).get(30, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                accounts.recordLogin("datelogin");
            });
        } finally {
            pool.shutdownNow();
        }
        assertReplacementPassword("datelogin");
    }

    @Test
    void blockingAnAccountDoesNotRestoreAStalePassword() throws Exception {
        register("statuschange");
        var pool = Executors.newSingleThreadExecutor();
        try {
            transaction().executeWithoutResult(state -> {
                int userId = users.findByLogin("statuschange").orElseThrow().getId();
                try {
                    pool.submit(() -> changePassword("statuschange")).get(30, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                administrators.updateUserStatus(userId, "blocked", "operator");
            });
        } finally {
            pool.shutdownNow();
        }
        assertReplacementPassword("statuschange");
        assertThat(users.findByLogin("statuschange").orElseThrow().getStatus().getName()).isEqualTo("blocked");
    }

    @Test
    void passwordChangeRevokesASessionFromAnAlreadyAuthenticatedLogin() throws Exception {
        register("loginfirst");
        var authenticated = new CountDownLatch(1);
        var finishLogin = new CountDownLatch(1);
        doAnswer(invocation -> {
            authenticated.countDown();
            assertThat(finishLogin.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(tokens).issue(any(Authentication.class));

        var pool = Executors.newFixedThreadPool(2);
        try {
            var login = pool.submit(() -> login("loginfirst"));
            assertThat(authenticated.await(10, TimeUnit.SECONDS)).isTrue();
            var changing = pool.submit(() -> changePassword("loginfirst"));
            assertThatThrownBy(() -> changing.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            finishLogin.countDown();
            MvcResult response = login.get(30, TimeUnit.SECONDS);
            assertThat(response.getResponse().getStatus()).isEqualTo(200);
            changing.get(30, TimeUnit.SECONDS);

            var body = objectMapper.readTree(response.getResponse().getContentAsString());
            mvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + body.get("accessToken").asString()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    java.util.Map.of("refreshToken", body.get("refreshToken").asString()))))
                    .andExpect(status().isUnauthorized());
            assertReplacementPassword("loginfirst");
        } finally {
            finishLogin.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void aLoginWaitingForAPasswordChangeCannotAuthenticateWithTheOldPassword() throws Exception {
        register("changefirst");
        var changingPassword = new CountDownLatch(1);
        var finishChange = new CountDownLatch(1);
        doAnswer(invocation -> {
            changingPassword.countDown();
            assertThat(finishChange.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(encoder).encode(REPLACEMENT);

        var pool = Executors.newFixedThreadPool(2);
        try {
            var changing = pool.submit(() -> changePassword("changefirst"));
            assertThat(changingPassword.await(10, TimeUnit.SECONDS)).isTrue();
            var login = pool.submit(() -> login("changefirst"));
            assertThatThrownBy(() -> login.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            finishChange.countDown();
            changing.get(30, TimeUnit.SECONDS);
            assertThat(login.get(30, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(401);
            assertReplacementPassword("changefirst");
        } finally {
            finishChange.countDown();
            pool.shutdownNow();
        }
    }

    private void register(String username) {
        accounts.register(new RegisterRequest(username, "Test", "User", ORIGINAL));
    }

    private void changePassword(String username) {
        accounts.changePassword(username, new ChangePasswordRequest(ORIGINAL, REPLACEMENT));
    }

    private MvcResult login(String username) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("username", username, "password", ORIGINAL))))
                .andReturn();
    }

    private void assertReplacementPassword(String username) {
        String password = jdbc.queryForObject("SELECT password FROM users WHERE login=?", String.class, username);
        assertThat(encoder.matches(REPLACEMENT, password)).isTrue();
        assertThat(encoder.matches(ORIGINAL, password)).isFalse();
    }

    private TransactionTemplate transaction() {
        var template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return template;
    }
}
