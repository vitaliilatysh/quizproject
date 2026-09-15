package ua.nure.latysh.quizzes.api.attempt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ua.nure.latysh.quizzes.api.domain.Attempt;
import ua.nure.latysh.quizzes.api.domain.AttemptRepository;
import ua.nure.latysh.quizzes.api.domain.QuestionRepository;
import ua.nure.latysh.quizzes.api.domain.Quiz;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.domain.UserRepository;
import ua.nure.latysh.quizzes.api.observability.QuizMetrics;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Starts an attempt only after the user and quiz have passed their guards. */
@Component
final class AttemptStarter {
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final AttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final AttemptSnapshotStore snapshots;
    private final Clock clock;
    private final QuizMetrics metrics;

    @Autowired
    AttemptStarter(
            QuizRepository quizRepository,
            UserRepository userRepository,
            AttemptRepository attemptRepository,
            QuestionRepository questionRepository,
            AttemptSnapshotStore snapshots,
            QuizMetrics metrics) {
        this(quizRepository, userRepository, attemptRepository, questionRepository,
                snapshots, Clock.systemUTC(), metrics);
    }

    AttemptStarter(
            QuizRepository quizRepository,
            UserRepository userRepository,
            AttemptRepository attemptRepository,
            QuestionRepository questionRepository,
            AttemptSnapshotStore snapshots,
            Clock clock,
            QuizMetrics metrics) {
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.questionRepository = questionRepository;
        this.snapshots = snapshots;
        this.clock = clock;
        this.metrics = metrics;
    }

    AttemptResponse start(int quizId, String username) {
        Quiz quiz = requireReadyQuiz(quizId);
        var user = userRepository.findByLogin(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user was not found"));
        Instant startedAt = clock.instant();
        Instant expiresAt = startedAt.plus(quiz.getTimeToPass(), ChronoUnit.MINUTES);
        var attempt = new Attempt(startedAt, expiresAt, quiz, user);
        attemptRepository.saveAndFlush(attempt);

        // Persist first and build the response from those same rows: what the
        // reader receives is exactly what completion will later score.
        AttemptResponse response = snapshots.toResponse(attempt, snapshots.create(attempt));
        metrics.recordStartedAttempt();
        return response;
    }

    private Quiz requireReadyQuiz(int quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz " + quizId + " was not found"));
        long invalidQuestions = questionRepository.countInvalidQuestions(quizId);
        long totalQuestions = questionRepository.countByQuiz_Id(quizId);
        if (totalQuestions == 0 || invalidQuestions > 0) {
            throw new ResourceConflictException("Quiz " + quizId + " is not ready for attempts");
        }
        return quiz;
    }
}
