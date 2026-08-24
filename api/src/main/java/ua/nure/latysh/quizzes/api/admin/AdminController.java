package ua.nure.latysh.quizzes.api.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.nure.latysh.quizzes.api.admin.AdminModels.LevelResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuestionResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuizRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.QuizResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.ResultResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.SubjectRequest;
import ua.nure.latysh.quizzes.api.admin.AdminModels.SubjectResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.UserResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.UserStatusRequest;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/status")
    @Operation(summary = "Verify administrator access")
    @SecurityRequirement(name = "bearerAuth")
    public AdminStatusResponse status() {
        return new AdminStatusResponse("quiz-api", "admin");
    }

    @GetMapping("/subjects")
    public List<SubjectResponse> subjects() {
        return adminService.subjects();
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectResponse createSubject(@Valid @RequestBody SubjectRequest request) {
        return adminService.createSubject(request.name());
    }

    @PutMapping("/subjects/{subjectId}")
    public SubjectResponse updateSubject(
            @PathVariable @Positive int subjectId,
            @Valid @RequestBody SubjectRequest request) {
        return adminService.updateSubject(subjectId, request.name());
    }

    @DeleteMapping("/subjects/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSubject(@PathVariable @Positive int subjectId) {
        adminService.deleteSubject(subjectId);
    }

    @GetMapping("/levels")
    public List<LevelResponse> levels() {
        return adminService.levels();
    }

    @GetMapping("/quizzes")
    public List<QuizResponse> quizzes() {
        return adminService.quizzes();
    }

    @PostMapping("/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizResponse createQuiz(@Valid @RequestBody QuizRequest request) {
        return adminService.createQuiz(request);
    }

    @PutMapping("/quizzes/{quizId}")
    public QuizResponse updateQuiz(
            @PathVariable @Positive int quizId,
            @Valid @RequestBody QuizRequest request) {
        return adminService.updateQuiz(quizId, request);
    }

    @DeleteMapping("/quizzes/{quizId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuiz(@PathVariable @Positive int quizId) {
        adminService.deleteQuiz(quizId);
    }

    @GetMapping("/quizzes/{quizId}/questions")
    public List<QuestionResponse> questions(@PathVariable @Positive int quizId) {
        return adminService.questions(quizId);
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse createQuestion(
            @PathVariable @Positive int quizId,
            @Valid @RequestBody QuestionRequest request) {
        return adminService.createQuestion(quizId, request);
    }

    @PutMapping("/questions/{questionId}")
    public QuestionResponse updateQuestion(
            @PathVariable @Positive int questionId,
            @Valid @RequestBody QuestionRequest request) {
        return adminService.updateQuestion(questionId, request);
    }

    @DeleteMapping("/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@PathVariable @Positive int questionId) {
        adminService.deleteQuestion(questionId);
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return adminService.users();
    }

    @PatchMapping("/users/{userId}/status")
    public UserResponse updateUserStatus(
            @PathVariable @Positive int userId,
            @Valid @RequestBody UserStatusRequest request,
            Authentication authentication) {
        return adminService.updateUserStatus(userId, request.status(), authentication.getName());
    }

    @GetMapping("/results")
    public List<ResultResponse> results(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return adminService.results(from, to);
    }
}
