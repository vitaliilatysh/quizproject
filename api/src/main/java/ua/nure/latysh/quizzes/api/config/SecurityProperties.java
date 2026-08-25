package ua.nure.latysh.quizzes.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("quiz.security")
@Validated
public record SecurityProperties(
        @NotBlank String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration tokenTtl,
        @NotEmpty List<String> allowedOrigins,
        @Valid @NotNull RateLimitProperties rateLimit) {
    public record RateLimitProperties(
            @NotNull Backend backend,
            @Positive int requests,
            @Positive int loginAttempts,
            @NotNull Duration window,
            @Positive int maxClients,
            @NotNull List<String> trustedProxyCidrs) {
        public enum Backend {
            MEMORY,
            REDIS
        }
    }
}
