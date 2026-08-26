package ua.nure.latysh.quizzes.api.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LevelRepository extends JpaRepository<Level, Integer> {
    List<Level> findAllByOrderByIdAsc();
}
