package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Level;

public interface LevelRepository extends CrudRepository<Level> {
    Level findByName(String levelName);
}
