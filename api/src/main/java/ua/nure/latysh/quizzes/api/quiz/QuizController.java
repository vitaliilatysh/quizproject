package ua.nure.latysh.quizzes.api.quiz;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.nure.latysh.quizzes.api.support.PaginationSupport;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/quizzes")
@Tag(name = "Quizzes")
public class QuizController {
    private final QuizQueryService quizQueryService;
    private final PaginationSupport pagination;

    public QuizController(QuizQueryService quizQueryService, PaginationSupport pagination) {
        this.quizQueryService = quizQueryService;
        this.pagination = pagination;
    }

    @GetMapping
    @Operation(summary = "List quizzes")
    public ResponseEntity<List<QuizResponse>> getQuizzes(
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        return pagination.response(quizQueryService.findAll(pagination.pageable(page, size)));
    }

    @GetMapping("/{quizId}")
    @Operation(summary = "Get a quiz", responses = @ApiResponse(responseCode = "404", description = "Quiz not found"))
    public QuizResponse getQuiz(@PathVariable @Positive int quizId) {
        return quizQueryService.findById(quizId);
    }
}

