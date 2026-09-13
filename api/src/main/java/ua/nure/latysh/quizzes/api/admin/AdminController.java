package ua.nure.latysh.quizzes.api.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import ua.nure.latysh.quizzes.api.support.PaginationSupport;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
public class AdminController {
    private final CatalogueAdminService catalogue;
    private final QuizAdminService quizzes;
    private final QuestionAdminService questions;
    private final UserAdminService users;
    private final ResultAdminService results;
    private final PaginationSupport pagination;

    public AdminController(
            CatalogueAdminService catalogue,
            QuizAdminService quizzes,
            QuestionAdminService questions,
            UserAdminService users,
            ResultAdminService results,
            PaginationSupport pagination) {
        this.catalogue = catalogue;
        this.quizzes = quizzes;
        this.questions = questions;
        this.users = users;
        this.results = results;
        this.pagination = pagination;
    }

    @GetMapping("/status")
    @Operation(summary = "Verify administrator access")
    @SecurityRequirement(name = "bearerAuth")
    public AdminStatusResponse status() {
        return new AdminStatusResponse("quiz-api", "admin");
    }

    @GetMapping("/subjects")
    public List<SubjectResponse> subjects() {
        return catalogue.subjects();
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectResponse createSubject(@Valid @RequestBody SubjectRequest request) {
        return catalogue.createSubject(request.name());
    }

    @PutMapping("/subjects/{subjectId}")
    public SubjectResponse updateSubject(
            @PathVariable @Positive int subjectId,
            @Valid @RequestBody SubjectRequest request) {
        return catalogue.updateSubject(subjectId, request.name());
    }

    @DeleteMapping("/subjects/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSubject(@PathVariable @Positive int subjectId) {
        catalogue.deleteSubject(subjectId);
    }

    @GetMapping("/levels")
    public List<LevelResponse> levels() {
        return catalogue.levels();
    }

    @GetMapping("/quizzes")
    public ResponseEntity<List<QuizResponse>> quizzes(
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        return pagination.response(quizzes.quizzes(pagination.pageable(page, size)));
    }

    @PostMapping("/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizResponse createQuiz(@Valid @RequestBody QuizRequest request) {
        return quizzes.createQuiz(request);
    }

    @PutMapping("/quizzes/{quizId}")
    public QuizResponse updateQuiz(
            @PathVariable @Positive int quizId,
            @Valid @RequestBody QuizRequest request) {
        return quizzes.updateQuiz(quizId, request);
    }

    @DeleteMapping("/quizzes/{quizId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuiz(@PathVariable @Positive int quizId) {
        quizzes.deleteQuiz(quizId);
    }

    @GetMapping("/quizzes/{quizId}/questions")
    public List<QuestionResponse> questions(@PathVariable @Positive int quizId) {
        return questions.questions(quizId);
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse createQuestion(
            @PathVariable @Positive int quizId,
            @Valid @RequestBody QuestionRequest request) {
        return questions.createQuestion(quizId, request);
    }

    @PutMapping("/questions/{questionId}")
    public QuestionResponse updateQuestion(
            @PathVariable @Positive int questionId,
            @Valid @RequestBody QuestionRequest request) {
        return questions.updateQuestion(questionId, request);
    }

    @DeleteMapping("/questions/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@PathVariable @Positive int questionId) {
        questions.deleteQuestion(questionId);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> users(
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        return pagination.response(users.users(pagination.pageable(page, size)));
    }

    @PatchMapping("/users/{userId}/status")
    public UserResponse updateUserStatus(
            @PathVariable @Positive int userId,
            @Valid @RequestBody UserStatusRequest request,
            Authentication authentication) {
        return users.updateUserStatus(userId, request.status(), authentication.getName());
    }

    @GetMapping("/results")
    public ResponseEntity<List<ResultResponse>> results(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        return pagination.response(results.results(from, to, pagination.pageable(page, size)));
    }
}
