package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Integer> {
    @Query("SELECT q FROM Quiz q JOIN FETCH q.subject JOIN FETCH q.level ORDER BY q.id")
    List<Quiz> findAllFetchingSubjectAndLevel();

    @Query("SELECT q FROM Quiz q JOIN FETCH q.subject JOIN FETCH q.level WHERE q.id = :quizId")
    Optional<Quiz> findByIdFetchingSubjectAndLevel(@Param("quizId") int quizId);

    boolean existsBySubject_Id(Integer subjectId);
}
