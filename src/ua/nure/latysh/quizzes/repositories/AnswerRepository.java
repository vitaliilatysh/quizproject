package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Answer;

import java.util.List;

public interface AnswerRepository extends CrudRepository<Answer> {
    List<Answer> findAllByQuestionId(int questionId);
}
