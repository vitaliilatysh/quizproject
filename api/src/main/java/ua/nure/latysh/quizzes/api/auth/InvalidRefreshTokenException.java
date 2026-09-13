package ua.nure.latysh.quizzes.api.auth;

import org.springframework.security.core.AuthenticationException;

public class InvalidRefreshTokenException extends AuthenticationException {
    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
