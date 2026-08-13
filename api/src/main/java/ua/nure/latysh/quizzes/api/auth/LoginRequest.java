package ua.nure.latysh.quizzes.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 15) String username,
        @NotBlank @Size(max = 128) String password) {
}
