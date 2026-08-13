package ua.nure.latysh.quizzes.api.result;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;

@Service
public class ResultQueryService {
    private final JdbcClient jdbcClient;

    public ResultQueryService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<ResultResponse> findCompletedByUsername(String username) {
        return jdbcClient.sql("""
                        SELECT attempts.id AS attempt_id,
                               quizzes.id AS quiz_id,
                               quizzes.name AS quiz_name,
                               attempts.score,
                               attempts.end_time
                        FROM attempts
                        JOIN quizzes ON quizzes.id = attempts.quiz_id
                        JOIN users ON users.id = attempts.user_id
                        WHERE users.login = :username
                          AND attempts.completed = TRUE
                          AND attempts.end_time IS NOT NULL
                        ORDER BY attempts.end_time DESC, attempts.id DESC
                        """)
                .param("username", username)
                .query((resultSet, rowNumber) -> new ResultResponse(
                        resultSet.getInt("attempt_id"),
                        resultSet.getInt("quiz_id"),
                        resultSet.getString("quiz_name"),
                        resultSet.getInt("score"),
                        resultSet.getTimestamp("end_time").toLocalDateTime().toInstant(ZoneOffset.UTC)))
                .list();
    }
}

