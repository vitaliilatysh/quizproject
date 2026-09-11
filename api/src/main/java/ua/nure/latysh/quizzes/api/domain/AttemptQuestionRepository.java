package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttemptQuestionRepository extends JpaRepository<AttemptQuestion, Integer> {
    /**
     * The snapshot of one attempt, in the order it was shown: questions by id,
     * and the options within each question by id. The reader saw that order and
     * the response has to keep it.
     */
    List<AttemptQuestion> findAllByAttempt_IdOrderByQuestionIdAscAnswerIdAsc(int attemptId);
}
