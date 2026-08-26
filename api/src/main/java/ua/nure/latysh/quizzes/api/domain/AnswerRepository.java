package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Integer> {
    List<Answer> findAllByQuestion_IdOrderByIdAsc(Integer questionId);

    @Query("""
            SELECT a FROM Answer a
            WHERE a.question.quiz.id = :quizId
            ORDER BY a.question.id, a.id
            """)
    List<Answer> findAllByQuestionQuizIdOrderByQuestionIdAndId(@Param("quizId") int quizId);

    @Modifying
    @Query("DELETE FROM Answer a WHERE a.question.quiz.id = :quizId")
    void deleteAllByQuizId(@Param("quizId") int quizId);

    @Modifying
    @Query("DELETE FROM Answer a WHERE a.question.id = :questionId")
    void deleteAllByQuestionId(@Param("questionId") int questionId);
}
