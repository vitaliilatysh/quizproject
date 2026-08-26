package ua.nure.latysh.quizzes.api.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class QuizMetrics {
    private final Counter successfulLogins;
    private final Counter failedLogins;
    private final Counter registrations;
    private final Counter tokenRefreshes;
    private final Counter startedAttempts;
    private final Counter completedAttempts;
    private final DistributionSummary attemptScores;

    public QuizMetrics(MeterRegistry meterRegistry) {
        successfulLogins = meterRegistry.counter(
                "quiz.authentication.attempts", "outcome", "success");
        failedLogins = meterRegistry.counter(
                "quiz.authentication.attempts", "outcome", "failure");
        registrations = meterRegistry.counter("quiz.account.registrations");
        tokenRefreshes = meterRegistry.counter("quiz.token.refreshes");
        startedAttempts = meterRegistry.counter("quiz.attempts", "state", "started");
        completedAttempts = meterRegistry.counter("quiz.attempts", "state", "completed");
        attemptScores = DistributionSummary.builder("quiz.attempt.score")
                .description("Completed quiz attempt scores")
                .baseUnit("percent")
                .minimumExpectedValue(1.0)
                .maximumExpectedValue(100.0)
                .register(meterRegistry);
    }

    public void recordSuccessfulLogin() {
        successfulLogins.increment();
    }

    public void recordFailedLogin() {
        failedLogins.increment();
    }

    public void recordRegistration() {
        registrations.increment();
    }

    public void recordTokenRefresh() {
        tokenRefreshes.increment();
    }

    public void recordStartedAttempt() {
        startedAttempts.increment();
    }

    public void recordCompletedAttempt(int score) {
        completedAttempts.increment();
        attemptScores.record(score);
    }
}
