package ua.nure.latysh.quizzes.api.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.account.AccountService;
import ua.nure.latysh.quizzes.api.domain.UserRepository;

/** Keeps credential verification and session creation inside the same account lock. */
@Service
@Transactional(isolation = Isolation.REPEATABLE_READ)
public class AuthenticationService {
    private final UserRepository users;
    private final AuthenticationManager authenticationManager;
    private final AccountService accounts;
    private final TokenService tokens;

    public AuthenticationService(
            UserRepository users,
            AuthenticationManager authenticationManager,
            AccountService accounts,
            TokenService tokens) {
        this.users = users;
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.tokens = tokens;
    }

    public TokenResponse login(LoginRequest request) {
        // Missing users still go through Spring Security's normal credential
        // check. Existing users stay locked until their session is committed.
        users.findByLoginForUpdate(request.username());
        return authenticateAndIssue(request.username(), request.password());
    }

    public TokenResponse register(RegisterRequest request) {
        accounts.register(request);
        return authenticateAndIssue(request.username(), request.password());
    }

    private TokenResponse authenticateAndIssue(String username, String password) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        accounts.recordLogin(authentication.getName());
        return tokens.issue(authentication);
    }
}
