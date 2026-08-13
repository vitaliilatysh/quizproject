package ua.nure.latysh.quizzes.api.result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/results")
@Tag(name = "Results")
public class ResultController {
    private final ResultQueryService resultQueryService;

    public ResultController(ResultQueryService resultQueryService) {
        this.resultQueryService = resultQueryService;
    }

    @GetMapping("/me")
    @Operation(summary = "List the current user's completed quiz results")
    @SecurityRequirement(name = "basicAuth")
    public List<ResultResponse> getMyResults(Authentication authentication) {
        return resultQueryService.findCompletedByUsername(authentication.getName());
    }
}

