package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultRepository extends JpaRepository<Result, Integer> {
    @Modifying
    @Query("DELETE FROM Result r WHERE r.attempt.quiz.id = :quizId OR r.answer.question.quiz.id = :quizId")
    void deleteAllByQuizId(@Param("quizId") int quizId);

    @Modifying
    @Query("DELETE FROM Result r WHERE r.answer.question.id = :questionId")
    void deleteAllByQuestionId(@Param("questionId") int questionId);
}
