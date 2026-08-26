package ua.nure.latysh.quizzes.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.nure.latysh.quizzes.api.account.AccountService;
import ua.nure.latysh.quizzes.api.observability.QuizMetrics;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AccountService accountService;
    private final QuizMetrics metrics;

    public AuthController(
            AuthenticationManager authenticationManager,
            TokenService tokenService,
            AccountService accountService,
            QuizMetrics metrics) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.accountService = accountService;
        this.metrics = metrics;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange database credentials for a short-lived JWT")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        var credentials = UsernamePasswordAuthenticationToken.unauthenticated(
                request.username(), request.password());
        final Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(credentials);
        } catch (AuthenticationException exception) {
            metrics.recordFailedLogin();
            throw exception;
        }
        metrics.recordSuccessfulLogin();
        accountService.recordLogin(authentication.getName());
        return tokenService.issue(authentication);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an active student account and return a short-lived JWT")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        accountService.register(request);
        metrics.recordRegistration();
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        return tokenService.issue(authentication);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a still-valid JWT for a new one with a fresh expiry")
    @SecurityRequirement(name = "bearerAuth")
    public TokenResponse refresh(Authentication authentication) {
        metrics.recordTokenRefresh();
        return tokenService.issue(authentication);
    }
}
