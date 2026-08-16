package ua.nure.latysh.quizzes.api.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
public class AdminController {
    @GetMapping("/status")
    @Operation(summary = "Verify administrator access")
    @SecurityRequirement(name = "bearerAuth")
    public AdminStatusResponse status() {
        return new AdminStatusResponse("quiz-api", "admin");
    }
}
