package ua.nure.latysh.quizzes.services;

import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.repositories.AttemptRepository;
import ua.nure.latysh.quizzes.repositories.impl.AttemptRepositoryImpl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class AttemptService {
    private final AttemptRepository attemptRepository;
    private final Clock clock;

    public AttemptService() {
        this(new AttemptRepositoryImpl(), Clock.systemUTC());
    }

    public AttemptService(AttemptRepository attemptRepository) {
        this(attemptRepository, Clock.systemUTC());
    }

    AttemptService(AttemptRepository attemptRepository, Clock clock) {
        this.attemptRepository = attemptRepository;
        this.clock = clock;
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

    public Attempt startAttempt(User user, Quiz quiz) {
        Date startedAt = Date.from(clock.instant());
        Date expiresAt = Date.from(clock.instant().plus(quiz.getTimeToPass(), ChronoUnit.MINUTES));
        Attempt attempt = new Attempt();
        attempt.setUserId(user.getId());
        attempt.setQuizId(quiz.getId());
        attempt.setScore(0);
        attempt.setStartTime(startedAt);
        attempt.setExpiresAt(expiresAt);
        attempt.setCompleted(false);
        return attemptRepository.create(attempt);
    }

    public Attempt completeAttempt(int attemptId, int userId, Set<Integer> answerIds) {
        return attemptRepository.complete(attemptId, userId, answerIds, Date.from(clock.instant()));
    }

    public Attempt findTheLatestForUser(User user) {
        return attemptRepository.findLastByUserId(user.getId());
    }

    public void updateAttemptByScore(Attempt attempt) {
        attemptRepository.update(attempt);
    }
}
