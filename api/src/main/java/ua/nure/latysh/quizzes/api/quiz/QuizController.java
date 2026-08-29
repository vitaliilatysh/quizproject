package ua.nure.latysh.quizzes.api.quiz;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.nure.latysh.quizzes.api.support.PaginationSupport;

import java.time.Duration;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/quizzes")
@Tag(name = "Quizzes")
public class QuizController {
    private final QuizQueryService quizQueryService;
    private final PaginationSupport pagination;
    private final CacheControl publicCacheControl;

    public QuizController(
            QuizQueryService quizQueryService,
            PaginationSupport pagination,
            @Value("${quiz.http.public-cache-max-age:PT1M}") Duration publicCacheMaxAge) {
        this.quizQueryService = quizQueryService;
        this.pagination = pagination;
        this.publicCacheControl = CacheControl.maxAge(publicCacheMaxAge).cachePublic().mustRevalidate();
    }

    @GetMapping
    @Operation(summary = "List quizzes",
            description = "Optional `search` matches a substring of the quiz name or its "
                    + "subject, case-insensitively. Optional `complexity` narrows by level "
                    + "label as stored (`low`, `medium`, `high`, `advanced`) and may be "
                    + "repeated to accept several. Both are applied by the database, so they "
                    + "narrow the whole collection rather than the page being returned.")
    public ResponseEntity<List<QuizResponse>> getQuizzes(
            @RequestParam(required = false) @Size(max = 50) String search,
            @RequestParam(required = false) List<@Size(max = 25) String> complexity,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        return pagination.response(
                quizQueryService.findAll(search, complexity, pagination.pageable(page, size)),
                publicCacheControl);
    }

    // Declared before the /{quizId} mapping so the literal segment is the one
    // that reads naturally here; Spring ranks it above the path variable either
    // way, and a contract test pins that so a future rename cannot turn this
    // into a 400 from int binding.
    @GetMapping("/summary")
    @Operation(summary = "Catalogue totals",
            description = "Total quizzes and the number of subjects that carry at least one, "
                    + "for callers that show a summary without fetching the whole catalogue.")
    public ResponseEntity<QuizCatalogueSummary> getSummary() {
        return ResponseEntity.ok()
                .cacheControl(publicCacheControl)
                .body(quizQueryService.summary());
    }

    @GetMapping("/{quizId}")
    @Operation(summary = "Get a quiz", responses = @ApiResponse(responseCode = "404", description = "Quiz not found"))
    public ResponseEntity<QuizResponse> getQuiz(@PathVariable @Positive int quizId) {
        return ResponseEntity.ok()
                .cacheControl(publicCacheControl)
                .body(quizQueryService.findById(quizId));
    }
}

