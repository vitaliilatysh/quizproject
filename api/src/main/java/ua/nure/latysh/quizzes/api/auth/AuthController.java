package ua.nure.latysh.quizzes.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.nure.latysh.quizzes.api.observability.QuizMetrics;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthenticationService authenticationService;
    private final TokenService tokenService;
    private final QuizMetrics metrics;

    public AuthController(
            AuthenticationService authenticationService,
            TokenService tokenService,
            QuizMetrics metrics) {
        this.authenticationService = authenticationService;
        this.tokenService = tokenService;
        this.metrics = metrics;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange database credentials for an access and refresh token pair")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        final TokenResponse response;
        try {
            response = authenticationService.login(request);
        } catch (AuthenticationException exception) {
            metrics.recordFailedLogin();
            throw exception;
        }
        metrics.recordSuccessfulLogin();
        return response;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an active student account and return an access and refresh token pair")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse response = authenticationService.register(request);
        metrics.recordRegistration();
        return response;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new token pair")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = tokenService.refresh(request.refreshToken());
        metrics.recordTokenRefresh();
        return response;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the current authenticated session")
    @SecurityRequirement(name = "bearerAuth")
    public void logout(@AuthenticationPrincipal Jwt token) {
        tokenService.revoke(token);
    }
}

