package ua.nure.latysh.quizzes.api.identity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public interface UserAdminModels {
    @Schema(name = "AdminUser", requiredProperties = {"id", "username", "role", "status"})
    record UserResponse(int id, String username, String role, String status) {
    }

    record UserStatusRequest(
            @NotBlank @Pattern(regexp = "active|blocked", flags = Pattern.Flag.CASE_INSENSITIVE)
            String status) {
    }
}
