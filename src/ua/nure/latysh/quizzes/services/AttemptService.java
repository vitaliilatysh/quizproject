package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.repositories.AttemptRepository;
import ua.nure.latysh.quizzes.repositories.impl.AttemptRepositoryImpl;

import java.util.List;

public class AttemptService {
    private final AttemptRepository attemptRepository;

    public AttemptService() {
        this(new AttemptRepositoryImpl());
    }

    public AttemptService(AttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    public List<Attempt> getAllAttempts() {
        return attemptRepository.findAll();
    }

    public List<Attempt> getAllAttemptsBetweenFinishDates(String startRange, String endRange) {
        return attemptRepository.findAllBetweenFinishDates(startRange, endRange);
    }

    public List<Attempt> findAllAttemptsPerUser(int userId) {
        return attemptRepository.findAllByUserId(userId);
    }

    public boolean saveAttempt(Attempt attempt) {
        return attemptRepository.save(attempt);
    }

    public Attempt findTheLatestForUser(User user) {
        return attemptRepository.findLastByUserId(user.getId());
    }

    public void updateAttemptByScore(Attempt attempt) {
        attemptRepository.update(attempt);
    }
}
