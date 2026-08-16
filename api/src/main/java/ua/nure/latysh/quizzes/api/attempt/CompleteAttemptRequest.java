package ua.nure.latysh.quizzes.api.attempt;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CompleteAttemptRequest(
        @NotNull @Size(max = 1000) Set<@Positive Integer> answerIds) {
}

