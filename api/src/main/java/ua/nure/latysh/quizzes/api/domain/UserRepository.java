package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
