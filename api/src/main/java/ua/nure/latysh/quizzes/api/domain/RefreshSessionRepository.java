package ua.nure.latysh.quizzes.api.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RefreshSession s JOIN FETCH s.user u JOIN FETCH u.status WHERE s.id = :sessionId")
    Optional<RefreshSession> findByIdForUpdate(@Param("sessionId") String sessionId);

    @Query("""
            SELECT COUNT(s) FROM RefreshSession s
            WHERE s.id = :sessionId
              AND s.user.login = :username
              AND LOWER(s.user.status.name) = 'active'
              AND s.revokedAt IS NULL
              AND s.expiresAt > :now
            """)
    long countActive(
            @Param("sessionId") String sessionId,
            @Param("username") String username,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshSession s SET s.revokedAt = :revokedAt
            WHERE s.id = :sessionId AND s.user.login = :username AND s.revokedAt IS NULL
            """)
    int revoke(
            @Param("sessionId") String sessionId,
            @Param("username") String username,
            @Param("revokedAt") Instant revokedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshSession s SET s.revokedAt = :revokedAt
            WHERE s.user.login = :username AND s.revokedAt IS NULL
            """)
    int revokeAll(@Param("username") String username, @Param("revokedAt") Instant revokedAt);
}
