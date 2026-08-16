package ua.nure.latysh.quizzes.api.attempt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Quiz attempts")
@SecurityRequirement(name = "bearerAuth")
public class AttemptController {
    private final AttemptService attemptService;

    public AttemptController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @PostMapping("/quizzes/{quizId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a quiz attempt for the current user")
    public AttemptResponse start(
            @PathVariable @Positive int quizId,
            Authentication authentication) {
        return attemptService.start(quizId, authentication.getName());
    }

    @GetMapping("/attempts/{attemptId}")
    @Operation(summary = "Read an owned quiz attempt without exposing correct answers")
    public AttemptResponse get(
            @PathVariable @Positive long attemptId,
            Authentication authentication) {
        return attemptService.findOwned(attemptId, authentication.getName());
    }

    @PostMapping("/attempts/{attemptId}/complete")
    @Operation(summary = "Submit selected answers and complete an owned attempt once")
    public AttemptCompletionResponse complete(
            @PathVariable @Positive long attemptId,
            @Valid @RequestBody CompleteAttemptRequest request,
            Authentication authentication) {
        return attemptService.complete(attemptId, authentication.getName(), request.answerIds());
    }
}

