package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Question;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends CrudRepository<Question>{
    List<Question> findAllByQuizId(int quizId);
    Optional<Question> findByName(String questionName);
    Question saveQuestion(Question question);
}
