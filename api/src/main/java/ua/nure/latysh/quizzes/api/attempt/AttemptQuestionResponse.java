package ua.nure.latysh.quizzes.api.attempt;

import java.util.List;

public record AttemptQuestionResponse(int id, String text, List<AnswerOptionResponse> answers) {
}

