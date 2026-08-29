package ua.nure.latysh.quizzes.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import ua.nure.latysh.quizzes.api.account.AccountService;
import ua.nure.latysh.quizzes.api.admin.AdminModels.UserResponse;
import ua.nure.latysh.quizzes.api.auth.RegisterRequest;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refusing to block the last administrator is a check-then-act: count who is
 * left, then block one. Two administrators blocking each other at the same
 * moment would each count the other as active and both would be let through,
 * leaving an installation nobody can administer — the exact outcome the check
 * exists to prevent.
 *
 * <p>{@code lockActiveAdministrators} takes those rows for update to serialize
 * the two calls. Whether that holds is decided by real row locking, so this
 * runs against MySQL. A MockMvc test cannot exercise it at all: it only ever
 * runs one request at a time.
 */
@Testcontainers
@SpringBootTest(properties = {
        "quiz.security.rate-limit.backend=memory",
        "quiz.security.jwt-secret=YWRtaW4tc3RhdHVzLWNvbmN1cnJlbmN5LXNlY3JldC0zMi1ieXRlcyE=",
        "quiz.security.allowed-origins=https://app.example.test"
})
class AdminStatusConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("tests_db")
            .withUsername("quiz")
            .withPassword("quiz-password");

    @Autowired
    private AdminService adminService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void refusesTheSecondOfTwoAdministratorsBlockingEachOther() throws Exception {
        int first = createAdministrator("adminone", "First");
        int second = createAdministrator("admintwo", "Second");
        assertThat(activeAdministratorCount())
                .as("the premise: the migrations seed no users, so these two are all of them")
                .isEqualTo(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Object> firstBlocksSecond =
                    pool.submit(blocking(bothReady, go, second, "adminone"));
            Future<Object> secondBlocksFirst =
                    pool.submit(blocking(bothReady, go, first, "admintwo"));
            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Object> outcomes = List.of(
                    firstBlocksSecond.get(30, TimeUnit.SECONDS),
                    secondBlocksFirst.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).filteredOn(UserResponse.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(ResourceConflictException.class::isInstance).hasSize(1);
        } finally {
            pool.shutdown();
        }

        assertThat(activeAdministratorCount())
                .as("an administrator survives, whichever call won the lock")
                .isEqualTo(1);
    }

    private Callable<Object> blocking(
            CountDownLatch bothReady, CountDownLatch go, int targetId, String actingAs) {
        return () -> {
            bothReady.countDown();
            go.await();
            try {
                return adminService.updateUserStatus(targetId, "blocked", actingAs);
            } catch (ResourceConflictException exception) {
                return exception;
            }
        };
    }

    private int createAdministrator(String login, String firstName) {
        // Registration only ever creates students and no endpoint changes a
        // role, so the promotion has to happen in the database.
        accountService.register(new RegisterRequest(login, firstName, "Admin", "SecretPass1"));
        jdbcTemplate.update(
                "UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'admin') WHERE login = ?",
                login);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE login = ?", Integer.class, login);
    }

    private long activeAdministratorCount() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM users u
                  JOIN roles r ON r.id = u.role_id
                  JOIN statuses s ON s.id = u.status_id
                WHERE r.name = 'admin' AND s.name = 'active'
                """, Long.class);
    }
}
