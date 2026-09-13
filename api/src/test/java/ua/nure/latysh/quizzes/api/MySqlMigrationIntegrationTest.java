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

import java.util.List;

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
        assertThat(flyway.info().current().getVersion()).hasToString("6");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class))
                .isEqualTo(6);
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

        List<String> paginatedQueryIndexes = jdbcTemplate.query("""
                SELECT CONCAT(index_name, ':', GROUP_CONCAT(
                    CONCAT(column_name, ':', collation)
                    ORDER BY seq_in_index SEPARATOR ','))
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'attempts'
                  AND index_name IN (
                    'idx_attempts_user_completed_end_time',
                    'idx_attempts_completed_end_time')
                GROUP BY index_name
                ORDER BY index_name
                """, (resultSet, rowNumber) -> resultSet.getString(1));
        assertThat(paginatedQueryIndexes).containsExactly(
                "idx_attempts_completed_end_time:completed:A,end_time:D,id:D",
                "idx_attempts_user_completed_end_time:user_id:A,completed:A,end_time:D,id:D");

        List<String> snapshotColumns = jdbcTemplate.query("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'attempt_questions'
                ORDER BY ordinal_position
                """, (resultSet, rowNumber) -> resultSet.getString(1));
        assertThat(snapshotColumns).containsExactly(
                "id", "attempt_id", "question_id", "question_text", "answer_id", "answer_text", "correct");

        // The snapshot references the attempt and nothing else, and that is the
        // design rather than an omission: a row here has to outlive the question
        // it copied. A foreign key to questions or answers would either cascade
        // the snapshot away with the row it describes — losing the reader's
        // attempt, which is the bug this table exists to fix — or stop the
        // administrator deleting it at all.
        List<String> snapshotReferences = jdbcTemplate.query("""
                SELECT CONCAT(column_name, '->', referenced_table_name)
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE()
                  AND table_name = 'attempt_questions'
                  AND referenced_table_name IS NOT NULL
                ORDER BY column_name
                """, (resultSet, rowNumber) -> resultSet.getString(1));
        assertThat(snapshotReferences).containsExactly("attempt_id->attempts");

        // Microseconds, not seconds. A token's iat is truncated down to the
        // second, so a column at second precision would round a password change
        // back past a token issued earlier in the same second and renew it.
        // MySQL reports the precision in datetime_precision; H2 does not model
        // it, so this is the only place the choice can be checked.
        Integer credentialsChangedPrecision = jdbcTemplate.queryForObject("""
                SELECT datetime_precision
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'users'
                  AND column_name = 'credentials_changed_at'
                """, Integer.class);
        assertThat(credentialsChangedPrecision).isEqualTo(6);

        List<String> refreshSessionColumns = jdbcTemplate.query("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'refresh_sessions'
                ORDER BY ordinal_position
                """, (resultSet, rowNumber) -> resultSet.getString(1));
        assertThat(refreshSessionColumns).containsExactly(
                "id", "user_id", "token_hash", "created_at", "last_used_at", "expires_at", "revoked_at");

        String deleteRule = jdbcTemplate.queryForObject("""
                SELECT delete_rule
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'refresh_sessions'
                  AND constraint_name = 'fk_refresh_sessions_user'
                """, String.class);
        assertThat(deleteRule).isEqualTo("CASCADE");
    }
}
