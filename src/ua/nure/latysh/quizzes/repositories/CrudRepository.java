package ua.nure.latysh.quizzes.repositories;

import java.util.List;

public interface CrudRepository<T> {
    boolean save(T element);

    void update(T element);

    List<T> findAll();

    T findById(int elementId);

    void delete(T element);
}
