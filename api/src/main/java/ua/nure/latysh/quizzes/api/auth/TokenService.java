package ua.nure.latysh.quizzes.api.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.time.Instant;
import java.util.UUID;

@Service
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final RefreshSessionService refreshSessions;
    private final UserDetailsService userDetailsService;

    public TokenService(
            JwtEncoder jwtEncoder,
            SecurityProperties properties,
            RefreshSessionService refreshSessions,
            UserDetailsService userDetailsService) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.refreshSessions = refreshSessions;
        this.userDetailsService = userDetailsService;
    }

    public TokenResponse issue(Authentication authentication) {
        return issue(authentication, refreshSessions.create(authentication.getName()));
    }

    public TokenResponse refresh(String refreshToken) {
        RefreshSessionService.RefreshGrant grant = refreshSessions.rotate(refreshToken);
        UserDetails user = userDetailsService.loadUserByUsername(grant.username());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, user.getAuthorities());
        return issue(authentication, grant);
    }

    public void revoke(Jwt token) {
        refreshSessions.revoke(token.getClaimAsString("sid"), token.getSubject());
    }

    private TokenResponse issue(
            Authentication authentication, RefreshSessionService.RefreshGrant refreshGrant) {
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
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .claim("sid", refreshGrant.sessionId())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(
                token,
                "Bearer",
                properties.tokenTtl().toSeconds(),
                refreshGrant.refreshToken(),
                properties.refreshTokenTtl().toSeconds());
    }
}
