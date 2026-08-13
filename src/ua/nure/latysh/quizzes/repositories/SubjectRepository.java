package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Subject;

import java.util.Optional;

public interface SubjectRepository extends CrudRepository<Subject> {
    Optional<Subject> findByName(String subjectName);
}
