package ua.nure.latysh.quizzes.api.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
