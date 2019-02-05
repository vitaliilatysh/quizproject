package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Result;

import java.util.List;

public interface ResultRepository extends CrudRepository<Result> {
    List<Result> findByAttemptId(int attemptId);
    List<Result> findAllByUserId(int userId);
}
