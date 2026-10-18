package ua.nure.latysh.quizzes.api.attempt;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Set;

public record CompleteAttemptRequest(
        @NotNull Set<@NotNull @Positive Integer> answerIds) {
}


