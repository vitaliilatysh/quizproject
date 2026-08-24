package ua.nure.latysh.quizzes.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "[\\p{L}\\p{N}]{5,15}")
        String username,
        @NotBlank
        @Pattern(regexp = "[\\p{L}][\\p{L}'’ -]{0,19}")
        String firstName,
        @NotBlank
        @Pattern(regexp = "[\\p{L}][\\p{L}'’ -]{0,19}")
        String lastName,
        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(regexp = "\\S+")
        String password) {
}
