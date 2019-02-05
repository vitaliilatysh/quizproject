package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Subject;

public interface SubjectRepository extends CrudRepository<Subject> {
    Subject findByName(String subjectName);
}
