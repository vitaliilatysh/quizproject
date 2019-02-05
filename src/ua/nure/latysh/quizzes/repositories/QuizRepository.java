package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Quiz;

import java.util.List;

public interface QuizRepository extends CrudRepository<Quiz> {
    Quiz findByName(String quizName);
    List<Quiz> findBySubjectId(int subjectId);
    List<Quiz> findBySubjectName(String subjectName);
}
