package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatusRepository extends JpaRepository<Status, Integer> {
    Optional<Status> findByNameIgnoreCase(String name);
}
