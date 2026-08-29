package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Integer> {
    /**
     * Lists quizzes, optionally narrowed by a name or subject substring and by a
     * set of level labels.
     *
     * <p>{@code complexities} must never be empty. Hibernate renders an empty
     * collection as {@code IN ()}, which MySQL rejects as a syntax error, and it
     * does so even when the guard in front of it is false. Callers that do not
     * filter by level pass {@link #ANY_COMPLEXITY} together with
     * {@code allComplexities = true}, so the {@code IN} clause is syntactically
     * valid but never reached.
     *
     * <p>{@code ESCAPE '!'} matches the escaping the service applies to the
     * pattern. Without it the escapes are literal text, so a search for "50%"
     * would match everything instead of that string.
     */
    @Query(value = """
            SELECT q FROM Quiz q JOIN FETCH q.subject s JOIN FETCH q.level l
            WHERE (:search IS NULL
                   OR LOWER(q.name) LIKE :search ESCAPE '!'
                   OR LOWER(s.name) LIKE :search ESCAPE '!')
              AND (:allComplexities = TRUE OR LOWER(l.label) IN :complexities)
            ORDER BY q.id
            """,
            countQuery = """
            SELECT COUNT(q) FROM Quiz q JOIN q.subject s JOIN q.level l
            WHERE (:search IS NULL
                   OR LOWER(q.name) LIKE :search ESCAPE '!'
                   OR LOWER(s.name) LIKE :search ESCAPE '!')
              AND (:allComplexities = TRUE OR LOWER(l.label) IN :complexities)
            """)
    Page<Quiz> search(
            @Param("search") String search,
            @Param("allComplexities") boolean allComplexities,
            @Param("complexities") Collection<String> complexities,
            Pageable pageable);

    /** Placeholder that keeps the {@code IN} clause valid when no level filter applies. */
    Collection<String> ANY_COMPLEXITY = List.of("");

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
