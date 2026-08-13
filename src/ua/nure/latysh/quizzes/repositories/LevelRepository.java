package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Level;

import java.util.Optional;

public interface LevelRepository extends CrudRepository<Level> {
    Optional<Level> findByName(String levelName);
}
