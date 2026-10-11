package ua.nure.latysh.quizzes.api.attempt;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"id", "text"})
public record AnswerOptionResponse(int id, String text) {
}

