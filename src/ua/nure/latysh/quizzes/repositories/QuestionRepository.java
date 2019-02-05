package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Subject;

import java.util.List;

public interface QuestionRepository extends CrudRepository<Question>{
    List<Question> findAllByQuizId(int quizId);
    Question findByName(String questionName);
    Question saveQuestion(Question question);
}
