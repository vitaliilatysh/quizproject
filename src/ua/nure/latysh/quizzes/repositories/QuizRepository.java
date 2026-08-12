package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Quiz;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends CrudRepository<Quiz> {
    Optional<Quiz> findByName(String quizName);
    List<Quiz> findBySubjectId(int subjectId);
    List<Quiz> findBySubjectName(String subjectName);
}
