package ua.nure.latysh.quizzes.api.attempt;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(requiredProperties = {"id", "text", "answers"})
public record AttemptQuestionResponse(int id, String text, List<AnswerOptionResponse> answers) {
}

