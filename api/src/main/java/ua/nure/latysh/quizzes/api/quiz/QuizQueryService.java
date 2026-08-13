package ua.nure.latysh.quizzes.api.quiz;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.util.List;

@Service
public class QuizQueryService {
    private static final String SELECT_QUIZZES = """
            SELECT quizzes.id,
                   quizzes.name,
                   subjects.name AS subject_name,
                   levels.level AS complexity,
                   quizzes.time_to_pass,
                   COUNT(questions.id) AS total_questions
            FROM quizzes
            JOIN subjects ON subjects.id = quizzes.subject_id
            JOIN levels ON levels.id = quizzes.level_id
            LEFT JOIN questions ON questions.quiz_id = quizzes.id
            """;
    private static final String GROUP_AND_ORDER = """
            GROUP BY quizzes.id, quizzes.name, subjects.name, levels.level, quizzes.time_to_pass
            ORDER BY quizzes.id
            """;

    private final JdbcClient jdbcClient;

    public QuizQueryService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<QuizResponse> findAll() {
        return jdbcClient.sql(SELECT_QUIZZES + GROUP_AND_ORDER)
                .query(QuizQueryService::mapQuiz)
                .list();
    }

    public QuizResponse findById(int quizId) {
        return jdbcClient.sql(SELECT_QUIZZES + "WHERE quizzes.id = :quizId\n" + GROUP_AND_ORDER)
                .param("quizId", quizId)
                .query(QuizQueryService::mapQuiz)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Quiz " + quizId + " was not found"));
    }

    private static QuizResponse mapQuiz(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new QuizResponse(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getString("subject_name"),
                resultSet.getString("complexity"),
                resultSet.getInt("time_to_pass"),
                resultSet.getInt("total_questions"));
    }
}

