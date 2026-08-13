package ua.nure.latysh.quizzes.api.quiz;

public record QuizResponse(
        int id,
        String name,
        String subject,
        String complexity,
        int timeToPassMinutes,
        int totalQuestions
) {
}

