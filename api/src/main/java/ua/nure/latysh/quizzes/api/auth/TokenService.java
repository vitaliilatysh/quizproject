package ua.nure.latysh.quizzes.api.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.time.Instant;

@Service
public class TokenService {
    /** The account's credentials stamp, read back by {@code /auth/refresh}. */
    public static final String CREDENTIALS_CLAIM = "cca";

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public TokenService(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * @param credentialsStamp what the account's credentials looked like in the
     *     read that authorised this token, carried as {@code cca} so that a
     *     later refresh can tell whether they have moved since. It is the read's
     *     own value rather than anything derived from the clock: see
     *     {@code AccountService.credentialsStamp}.
     */
    public TokenResponse issue(Authentication authentication, String credentialsStamp) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.tokenTtl());
        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(authentication.getName())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .claim(CREDENTIALS_CLAIM, credentialsStamp)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.tokenTtl().toSeconds());
    }
}
