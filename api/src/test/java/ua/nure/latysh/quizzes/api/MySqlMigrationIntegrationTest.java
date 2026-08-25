package ua.nure.latysh.quizzes.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "quiz.security.rate-limit.backend=memory",
        "quiz.security.jwt-secret=cDctaW50ZWdyYXRpb24tc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM=",
        "quiz.security.allowed-origins=https://app.example.test"
})
class MySqlMigrationIntegrationTest {
    @Container
    @ServiceConnection
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("tests_db")
            .withUsername("quiz")
            .withPassword("quiz-password");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void startsOnlyAfterApplyingTheCompleteProductionSchema() {
        assertThat(flyway.info().current().getVersion()).hasToString("2");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quizzes", Integer.class)).isEqualTo(4);

        Integer securedAttemptColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'attempts'
                  AND column_name IN ('start_time', 'expires_at', 'completed')
                """, Integer.class);
        assertThat(securedAttemptColumns).isEqualTo(3);

        Integer answerForeignKeys = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE()
                  AND table_name = 'results'
                  AND column_name = 'answer_id'
                  AND referenced_table_name = 'answers'
                """, Integer.class);
        assertThat(answerForeignKeys).isEqualTo(1);
    }
}
