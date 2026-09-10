package ua.nure.latysh.quizzes.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final UserDetailsService userDetailsService;
    private final TokenService tokenService;
    private final AccountService accountService;
    private final QuizMetrics metrics;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            TokenService tokenService,
            AccountService accountService,
            QuizMetrics metrics) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
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

    /**
     * Exchanges a still-valid token for a fresh one, after re-reading the
     * account it names.
     *
     * <p>The re-read is the whole point of this method having a body. Every
     * other endpoint trusts the token: this is a stateless resource server, and
     * the subject and roles come from claims that nothing re-checks. That is a
     * deliberate trade, and it costs at most one token lifetime — but only if
     * this endpoint declines to extend it. Issuing from the presented token's
     * own claims made the lifetime unbounded instead: a blocked account with a
     * tab open refreshed itself for ever, and the web client refreshes on a
     * timer without being asked. The same held for an account that had been
     * deleted, and for an administrator whose role had been taken away.
     *
     * <p>A disabled or missing account is refused the same way a sign-in is
     * refused, and for the same reason: whatever the token still says, this
     * account may not have one. Authorities come from the database rather than
     * from the claims, so a role change takes effect on the next refresh
     * instead of never.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a still-valid JWT for a new one with a fresh expiry")
    @SecurityRequirement(name = "bearerAuth")
    public TokenResponse refresh(Authentication authentication) {
        UserDetails account;
        try {
            account = userDetailsService.loadUserByUsername(authentication.getName());
        } catch (UsernameNotFoundException exception) {
            throw new DisabledException("Account is no longer active", exception);
        }
        if (!account.isEnabled()) {
            throw new DisabledException("Account is no longer active");
        }
        metrics.recordTokenRefresh();
        return tokenService.issue(UsernamePasswordAuthenticationToken.authenticated(
                account.getUsername(), null, account.getAuthorities()));
    }
}
