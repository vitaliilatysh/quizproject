package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Attempt;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AttemptRepository extends CrudRepository<Attempt> {
    List<Attempt> findAllByUserId(int userId);

    Optional<Attempt> findLastByUserId(int userId);

    List<Attempt> findAllBetweenFinishDates(String startRange, String endRange);

    Attempt create(Attempt attempt);

    Attempt complete(int attemptId, int userId, Set<Integer> answerIds, Date completedAt);
}
