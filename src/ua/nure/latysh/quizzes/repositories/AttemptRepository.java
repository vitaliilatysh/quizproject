package ua.nure.latysh.quizzes.repositories;

import ua.nure.latysh.quizzes.entities.Attempt;

import java.util.Date;
import java.util.List;

public interface AttemptRepository extends CrudRepository<Attempt> {
    List<Attempt> findAllByUserId(int userId);

    Attempt findLastByUserId(int userId);

    List<Attempt> findAllBetweenFinishDates(String startRange, String endRange);
}
