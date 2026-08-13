package ua.nure.latysh.quizzes.api.quiz;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/quizzes")
@Tag(name = "Quizzes")
public class QuizController {
    private final QuizQueryService quizQueryService;

    public QuizController(QuizQueryService quizQueryService) {
        this.quizQueryService = quizQueryService;
    }

    @GetMapping
    @Operation(summary = "List quizzes")
    public List<QuizResponse> getQuizzes() {
        return quizQueryService.findAll();
    }

    @GetMapping("/{quizId}")
    @Operation(summary = "Get a quiz", responses = @ApiResponse(responseCode = "404", description = "Quiz not found"))
    public QuizResponse getQuiz(@PathVariable @Positive int quizId) {
        return quizQueryService.findById(quizId);
    }
}

