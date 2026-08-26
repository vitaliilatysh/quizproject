package ua.nure.latysh.quizzes.api.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuizMetricsTest {
    @Test
    void recordsLowCardinalityBusinessMetrics() {
        var registry = new SimpleMeterRegistry();
        var metrics = new QuizMetrics(registry);

        metrics.recordSuccessfulLogin();
        metrics.recordFailedLogin();
        metrics.recordRegistration();
        metrics.recordTokenRefresh();
        metrics.recordStartedAttempt();
        metrics.recordCompletedAttempt(75);

        assertEquals(1.0, registry.get("quiz.authentication.attempts")
                .tag("outcome", "success").counter().count());
        assertEquals(1.0, registry.get("quiz.authentication.attempts")
                .tag("outcome", "failure").counter().count());
        assertEquals(1.0, registry.get("quiz.account.registrations").counter().count());
        assertEquals(1.0, registry.get("quiz.token.refreshes").counter().count());
        assertEquals(1.0, registry.get("quiz.attempts")
                .tag("state", "started").counter().count());
        assertEquals(1.0, registry.get("quiz.attempts")
                .tag("state", "completed").counter().count());
        assertEquals(1, registry.get("quiz.attempt.score").summary().count());
        assertEquals(75.0, registry.get("quiz.attempt.score").summary().totalAmount());
    }
}
