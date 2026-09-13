package ua.nure.latysh.quizzes.api.auth;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ua.nure.latysh.quizzes.api.domain.RefreshSessionRepository;

import java.time.Clock;

@Component
public class ActiveSessionJwtValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID_SESSION = new OAuth2Error(
            "invalid_token", "Token session is inactive", null);

    private final RefreshSessionRepository sessionRepository;
    private final Clock clock;

    public ActiveSessionJwtValidator(RefreshSessionRepository sessionRepository) {
        this(sessionRepository, Clock.systemUTC());
    }

    ActiveSessionJwtValidator(RefreshSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String sessionId = token.getClaimAsString("sid");
        String username = token.getSubject();
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(username)) {
            return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        }
        boolean active = sessionRepository.countActive(sessionId, username, clock.instant()) > 0;
        return active
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_SESSION);
    }
}
