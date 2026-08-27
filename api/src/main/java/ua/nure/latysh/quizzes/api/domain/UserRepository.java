package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Integer> {
    Optional<UserAccount> findByLogin(String login);

    Page<UserAccount> findAllByOrderByLoginAsc(Pageable pageable);
}
