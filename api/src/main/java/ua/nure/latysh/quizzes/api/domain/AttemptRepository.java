package ua.nure.latysh.quizzes.api.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Integer> {
    @Query("SELECT a FROM Attempt a JOIN FETCH a.quiz WHERE a.id = :id AND a.user.login = :login")
    Optional<Attempt> findByIdAndUserLogin(@Param("id") Integer id, @Param("login") String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Attempt a JOIN FETCH a.quiz WHERE a.id = :id AND a.user.login = :login")
    Optional<Attempt> findByIdAndUserLoginForUpdate(@Param("id") Integer id, @Param("login") String login);

    @Query(value = """
            SELECT a FROM Attempt a JOIN FETCH a.quiz
            WHERE a.user.login = :username AND a.completed = true AND a.endTime IS NOT NULL
            ORDER BY a.endTime DESC, a.id DESC
            """, countQuery = """
            SELECT COUNT(a) FROM Attempt a
            WHERE a.user.login = :username AND a.completed = true AND a.endTime IS NOT NULL
            """)
    Page<Attempt> findCompletedByUsername(@Param("username") String username, Pageable pageable);

    @Query(value = """
            SELECT a FROM Attempt a JOIN FETCH a.quiz JOIN FETCH a.user
            WHERE a.completed = true AND a.endTime IS NOT NULL
              AND (:from IS NULL OR a.endTime >= :from)
              AND (:to IS NULL OR a.endTime <= :to)
            ORDER BY a.endTime DESC, a.id DESC
            """, countQuery = """
            SELECT COUNT(a) FROM Attempt a
            WHERE a.completed = true AND a.endTime IS NOT NULL
              AND (:from IS NULL OR a.endTime >= :from)
              AND (:to IS NULL OR a.endTime <= :to)
            """)
    Page<Attempt> findCompletedInRange(
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Attempt a WHERE a.quiz.id = :quizId")
    void deleteAllByQuizId(@Param("quizId") int quizId);
}
