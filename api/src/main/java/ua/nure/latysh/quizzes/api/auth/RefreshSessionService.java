package ua.nure.latysh.quizzes.api.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;
import ua.nure.latysh.quizzes.api.domain.RefreshSession;
import ua.nure.latysh.quizzes.api.domain.RefreshSessionRepository;
import ua.nure.latysh.quizzes.api.domain.UserAccount;
import ua.nure.latysh.quizzes.api.domain.UserRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshSessionService {
    private static final int TOKEN_BYTES = 32;

    private final RefreshSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenHasher tokenHasher;
    private final SecurityProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public RefreshSessionService(
            RefreshSessionRepository sessionRepository,
            UserRepository userRepository,
            RefreshTokenHasher tokenHasher,
            SecurityProperties properties) {
        this(sessionRepository, userRepository, tokenHasher, properties, new SecureRandom(), Clock.systemUTC());
    }

    RefreshSessionService(
            RefreshSessionRepository sessionRepository,
            UserRepository userRepository,
            RefreshTokenHasher tokenHasher,
            SecurityProperties properties,
            SecureRandom secureRandom,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public RefreshGrant create(String username) {
        UserAccount user = userRepository.findByLogin(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is missing"));
        Instant now = clock.instant();
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = generateToken(sessionId);
        sessionRepository.save(new RefreshSession(
                sessionId,
                user,
                tokenHasher.hash(refreshToken),
                now,
                now.plus(properties.refreshTokenTtl())));
        return new RefreshGrant(username, sessionId, refreshToken);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshGrant rotate(String refreshToken) {
        String sessionId = extractSessionId(refreshToken);
        RefreshSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (session.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException();
        }
        if (!now.isBefore(session.getExpiresAt()) || !isActive(session.getUser())) {
            session.revoke(now);
            throw new InvalidRefreshTokenException();
        }
        if (!tokenHasher.matches(refreshToken, session.getTokenHash())) {
            session.revoke(now);
            throw new InvalidRefreshTokenException();
        }

        String rotatedToken = generateToken(sessionId);
        session.rotate(
                tokenHasher.hash(rotatedToken),
                now,
                now.plus(properties.refreshTokenTtl()));
        return new RefreshGrant(session.getUser().getLogin(), sessionId, rotatedToken);
    }

    @Transactional
    public void revoke(String sessionId, String username) {
        sessionRepository.revoke(sessionId, username, clock.instant());
    }

    @Transactional
    public void revokeAll(String username) {
        sessionRepository.revokeAll(username, clock.instant());
    }

    private String generateToken(String sessionId) {
        byte[] secret = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(secret);
        return sessionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private static String extractSessionId(String refreshToken) {
        int separator = refreshToken.indexOf('.');
        if (separator <= 0 || separator == refreshToken.length() - 1) {
            throw new InvalidRefreshTokenException();
        }
        String sessionId = refreshToken.substring(0, separator);
        try {
            UUID.fromString(sessionId);
            return sessionId;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException();
        }
    }

    private static boolean isActive(UserAccount user) {
        return "active".equalsIgnoreCase(user.getStatus().getName());
    }

    public record RefreshGrant(String username, String sessionId, String refreshToken) {
    }
}
