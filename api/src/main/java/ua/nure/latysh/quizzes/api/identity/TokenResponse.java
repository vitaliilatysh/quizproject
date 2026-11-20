package ua.nure.latysh.quizzes.api.identity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"accessToken", "tokenType", "expiresIn", "refreshToken", "refreshExpiresIn"})
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn) {
}
