package ua.nure.latysh.quizzes.repositories;

import java.util.List;
import java.util.Optional;

public interface CrudRepository<T> {
    boolean save(T element);

    void update(T element);

    List<T> findAll();

    Optional<T> findById(int elementId);

    void delete(T element);
}
