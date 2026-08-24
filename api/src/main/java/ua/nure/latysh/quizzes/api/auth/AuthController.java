package ua.nure.latysh.quizzes.api.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.nure.latysh.quizzes.api.account.AccountService;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AccountService accountService;

    public AuthController(
            AuthenticationManager authenticationManager,
            TokenService tokenService,
            AccountService accountService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.accountService = accountService;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange database credentials for a short-lived JWT")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        accountService.recordLogin(authentication.getName());
        return tokenService.issue(authentication);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an active student account and return a short-lived JWT")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        accountService.register(request);
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        return tokenService.issue(authentication);
    }
}
