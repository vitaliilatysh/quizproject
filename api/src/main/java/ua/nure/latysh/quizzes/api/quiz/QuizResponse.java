package ua.nure.latysh.quizzes.api.quiz;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"id", "name", "subject", "complexity", "timeToPassMinutes", "totalQuestions"})
public record QuizResponse(
        int id,
        String name,
        String subject,
        String complexity,
        int timeToPassMinutes,
        int totalQuestions
) {
}

