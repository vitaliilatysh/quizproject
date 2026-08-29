package ua.nure.latysh.quizzes.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read services declare {@code REPEATABLE_READ} so that the several queries
 * behind one response cannot each see a different state of the database. Whether
 * that holds is decided by the database, not by Spring, so asserting it on H2
 * would verify the wrong engine: this runs against the MySQL the API actually
 * ships against.
 *
 * <p>Each test states both directions — the level the services ask for hides a
 * concurrent commit, and the level they would otherwise have inherited does not.
 * The second half is what keeps the first from being vacuous, and it is asserted
 * rather than claimed.
 */
@Testcontainers
@SpringBootTest(properties = {
        "quiz.security.rate-limit.backend=memory",
        "quiz.security.jwt-secret=cXVpei1pc29sYXRpb24taW50ZWdyYXRpb24tc2VjcmV0LTMyIQ==",
        "quiz.security.allowed-origins=https://app.example.test"
})
class ReadIsolationIntegrationTest {
    @Container
    @ServiceConnection
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("tests_db")
            .withUsername("quiz")
            .withPassword("quiz-password");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void repeatableReadHidesACommitLandingMidRead() {
        assertThat(secondReadUnder(TransactionDefinition.ISOLATION_REPEATABLE_READ))
                .as("the isolation the read services declare must hold the first snapshot")
                .isEqualTo(originalName());
    }

    @Test
    void readCommittedWouldExposeIt() {
        // Not a property anyone wants — it is the reason the annotation names an
        // isolation level instead of inheriting one. If MySQL ever stopped
        // differing here, the test above would pass for the wrong reason and
        // this one would tell us.
        assertThat(secondReadUnder(TransactionDefinition.ISOLATION_READ_COMMITTED))
                .as("read committed takes a fresh snapshot per statement")
                .isEqualTo(RENAMED);
    }

    private static final String RENAMED = "Renamed Mid-Read";

    private String originalName() {
        return jdbcTemplate.queryForObject("SELECT name FROM subjects WHERE id = 1", String.class);
    }

    /**
     * Reads a row inside one read-only transaction, lets another connection
     * commit a change to it, then reads it again in the same transaction and
     * returns what the second read saw.
     */
    private String secondReadUnder(int isolation) {
        String original = originalName();
        ExecutorService writer = Executors.newSingleThreadExecutor();
        var template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setIsolationLevel(isolation);
        try {
            return template.execute(status -> {
                originalName();
                try {
                    writer.submit(() -> jdbcTemplate.update(
                            "UPDATE subjects SET name = ? WHERE id = 1", RENAMED)).get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                return originalName();
            });
        } finally {
            writer.shutdown();
            jdbcTemplate.update("UPDATE subjects SET name = ? WHERE id = 1", original);
        }
    }
}
