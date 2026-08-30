package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Integer> {
    /**
     * Fetches the role and status with the account. Both associations are lazy,
     * and the caller that matters most — Spring Security's user lookup during
     * login — runs outside a transaction, where touching a lazy proxy fails and
     * surfaces as a plain authentication failure rather than an error.
     */
    @Query("SELECT u FROM UserAccount u JOIN FETCH u.role JOIN FETCH u.status WHERE u.login = :login")
    Optional<UserAccount> findByLogin(@Param("login") String login);

    /** Same reason: the admin listing maps role and status outside a transaction. */
    @Query(value = "SELECT u FROM UserAccount u JOIN FETCH u.role JOIN FETCH u.status ORDER BY u.login",
            countQuery = "SELECT COUNT(u) FROM UserAccount u")
    Page<UserAccount> findAllByOrderByLoginAsc(Pageable pageable);

    /**
     * Every administrator who can still sign in, locked for update.
     *
     * <p>The lock is what makes the caller's decision safe. Counting the
     * remaining administrators and then blocking one is a check-then-act: two
     * administrators blocking each other at the same moment would each see the
     * other as active and both would be allowed through, leaving the system
     * with none. Taking the rows for update serializes those two calls, so the
     * second one counts what the first actually left behind.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u FROM UserAccount u
            WHERE LOWER(u.role.name) = 'admin' AND LOWER(u.status.name) = 'active'
            ORDER BY u.id
            """)
    List<UserAccount> lockActiveAdministrators();
}
