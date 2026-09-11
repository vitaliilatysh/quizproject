package ua.nure.latysh.quizzes.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
        return tokenService.issue(authentication,
                accountService.credentialsStamp(authentication.getName()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an active student account and return a short-lived JWT")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        accountService.register(request);
        metrics.recordRegistration();
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        return tokenService.issue(authentication,
                accountService.credentialsStamp(authentication.getName()));
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
     *
     * <p>A password changed since the token was issued is refused too, and it
     * is the case the other two do not cover: the account is neither blocked
     * nor gone, so a thief holding a token kept renewing it for ever while its
     * owner — having done the one thing anyone does about a stolen account —
     * watched it make no difference.
     *
     * <p>That last check compares the account's credentials stamp against the
     * one the presented token carries, rather than asking whether the token is
     * older than the change. The two are not the same: a token's {@code iat} is
     * stamped after the check that let it be minted, so an ordering comparison
     * had a window where a refresh read the pre-change value, the change
     * committed, and the token that came out was dated after it — renewable for
     * ever. Comparing the stamp closes that by construction, because the token
     * quotes what its own authorising read saw.
     *
     * <p>The parameter is a {@link JwtAuthenticationToken} rather than an
     * {@link Authentication} so the claim can be read without a cast and a
     * branch for a principal this endpoint cannot be reached without.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a still-valid JWT for a new one with a fresh expiry")
    @SecurityRequirement(name = "bearerAuth")
    public TokenResponse refresh(JwtAuthenticationToken authentication) {
        UserDetails account;
        try {
            account = userDetailsService.loadUserByUsername(authentication.getName());
        } catch (UsernameNotFoundException exception) {
            throw new DisabledException("Account is no longer active", exception);
        }
        if (!account.isEnabled()) {
            throw new DisabledException("Account is no longer active");
        }
        String credentialsStamp = accountService.credentialsStamp(authentication.getName());
        if (!credentialsStamp.equals(presentedCredentialsStamp(authentication))) {
            throw new CredentialsExpiredException("The password has changed since this token was issued");
        }
        metrics.recordTokenRefresh();
        // Issued with the stamp this read saw, not one taken afterwards. A change
        // that commits from here on leaves the stored value disagreeing with the
        // token just minted, so the next refresh refuses it.
        return tokenService.issue(UsernamePasswordAuthenticationToken.authenticated(
                account.getUsername(), null, account.getAuthorities()), credentialsStamp);
    }

    /**
     * The stamp the presented token was minted with, or none if it predates the
     * claim. Missing reads as "no change recorded", which is what every account
     * also reports until its password moves, so tokens issued before this
     * deployment keep refreshing rather than all being refused at once.
     */
    private static String presentedCredentialsStamp(JwtAuthenticationToken authentication) {
        String claim = authentication.getToken().getClaimAsString(TokenService.CREDENTIALS_CLAIM);
        return claim == null ? "" : claim;
    }
}
