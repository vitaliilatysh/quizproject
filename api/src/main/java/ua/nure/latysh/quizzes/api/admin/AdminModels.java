package ua.nure.latysh.quizzes.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public interface AdminModels {
    record SubjectResponse(int id, String name) {
    }

    record LevelResponse(int id, String name) {
    }

    record QuizResponse(
            int id,
            String name,
            int timeToPassMinutes,
            int levelId,
            String complexity,
            int subjectId,
            String subject,
            int totalQuestions) {
    }

    record AnswerResponse(int id, String text, boolean correct) {
    }

    record QuestionResponse(int id, int quizId, String text, List<AnswerResponse> answers) {
    }

    record UserResponse(int id, String username, String role, String status) {
    }

    record ResultResponse(
            long attemptId,
            String username,
            int quizId,
            String quizName,
            int score,
            Instant completedAt) {
    }

    record SubjectRequest(@NotBlank @Size(max = 25) String name) {
    }

    record QuizRequest(
            @NotBlank @Size(max = 50) String name,
            @Positive int subjectId,
            @Positive int levelId,
            @Min(1) @Max(1440) int timeToPassMinutes) {
    }

    record AnswerRequest(@NotBlank @Size(max = 50) String text, boolean correct) {
    }

    record QuestionRequest(
            @NotBlank @Size(max = 250) String text,
            @NotNull @Size(min = 4, max = 4) List<@Valid AnswerRequest> answers) {
    }

    record UserStatusRequest(
            @NotBlank @Pattern(regexp = "active|blocked", flags = Pattern.Flag.CASE_INSENSITIVE)
            String status) {
    }
}
