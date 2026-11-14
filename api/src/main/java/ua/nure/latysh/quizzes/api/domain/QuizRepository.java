package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Integer>, JpaSpecificationExecutor<Quiz> {
    /** Fetch the two to-one relations without putting fetch joins in the count specification. */
    @Override
    @EntityGraph(attributePaths = {"subject", "level"})
    Page<Quiz> findAll(Specification<Quiz> specification, Pageable pageable);

    /** Stable catalogue ordering for both paginated and full-list callers. */
    default Page<Quiz> findCatalogue(Specification<Quiz> specification, Pageable pageable) {
        Sort order = Sort.by("id");
        Pageable ordered = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order)
                : Pageable.unpaged(order);
        return findAll(specification, ordered);
    }

    /**
     * Counts subjects that actually carry a quiz, which is not the same as the
     * number of rows in {@code subjects}. The home page derived this figure
     * from the quiz list, so a subject with no quizzes has never been included;
     * counting the table instead would silently inflate the number shown.
     */
    @Query("SELECT COUNT(DISTINCT q.subject.id) FROM Quiz q")
    long countDistinctSubjects();

    @Query("SELECT q FROM Quiz q JOIN FETCH q.subject JOIN FETCH q.level WHERE q.id = :quizId")
    Optional<Quiz> findByIdFetchingSubjectAndLevel(@Param("quizId") int quizId);

    boolean existsBySubject_Id(Integer subjectId);
}
