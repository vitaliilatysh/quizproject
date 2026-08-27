package ua.nure.latysh.quizzes.api.result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.nure.latysh.quizzes.api.support.PaginationSupport;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/results")
@Tag(name = "Results")
public class ResultController {
    private final ResultQueryService resultQueryService;
    private final PaginationSupport pagination;

    public ResultController(ResultQueryService resultQueryService, PaginationSupport pagination) {
        this.resultQueryService = resultQueryService;
        this.pagination = pagination;
    }

    @GetMapping("/me")
    @Operation(summary = "List the current user's completed quiz results")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ResultResponse>> getMyResults(
            Authentication authentication,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        return pagination.response(resultQueryService.findCompletedByUsername(
                authentication.getName(), pagination.pageable(page, size)));
    }
}
