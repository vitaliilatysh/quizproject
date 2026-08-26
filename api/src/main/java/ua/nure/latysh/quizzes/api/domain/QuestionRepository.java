package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Integer> {
    List<Question> findAllByQuiz_IdOrderByIdAsc(Integer quizId);

    long countByQuiz_Id(Integer quizId);

    @Query("""
            SELECT q.quiz.id AS quizId, COUNT(q) AS total
            FROM Question q
            GROUP BY q.quiz.id
            """)
    List<QuizQuestionCount> countAllGroupedByQuiz();

    @Query("""
            SELECT COUNT(q)
            FROM Question q
            WHERE q.quiz.id = :quizId
              AND (
                (SELECT COUNT(a) FROM Answer a WHERE a.question = q) <> 4
                OR (SELECT COUNT(a) FROM Answer a WHERE a.question = q AND a.correct = true) = 0
              )
            """)
    long countInvalidQuestions(@Param("quizId") int quizId);

    @Modifying
    @Query("DELETE FROM Question q WHERE q.quiz.id = :quizId")
    void deleteAllByQuizId(@Param("quizId") int quizId);

    interface QuizQuestionCount {
        Integer getQuizId();

        long getTotal();
    }
}
