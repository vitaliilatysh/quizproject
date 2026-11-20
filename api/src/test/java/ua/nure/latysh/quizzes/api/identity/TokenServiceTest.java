package ua.nure.latysh.quizzes.api.identity;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceTest {
    @Test
    void timestampsIssuedAndRefreshedTokensWithTheProvidedClock() {
        Instant now = Instant.parse("2030-02-03T04:05:06Z");
        var encoder = mock(JwtEncoder.class);
        var sessions = mock(RefreshSessionService.class);
        var properties = mock(SecurityProperties.class);
        when(properties.issuer()).thenReturn("quiz-api");
        when(properties.tokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(properties.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
        var users = mock(UserDetailsService.class);
        var service = new TokenService(encoder, properties, sessions, users, Clock.fixed(now, ZoneOffset.UTC));
        var authentication = UsernamePasswordAuthenticationToken.authenticated("student", null, List.of());
        var grant = new RefreshSessionService.RefreshGrant("student", "session", "refresh");
        when(sessions.create("student")).thenReturn(grant);
        when(sessions.rotate("refresh")).thenReturn(grant);
        when(users.loadUserByUsername("student")).thenReturn(User.withUsername("student")
                .password("unused").roles("STUDENT").build());
        when(encoder.encode(any())).thenReturn(Jwt.withTokenValue("access")
                .header("alg", "HS256").subject("student").build());

        service.issue(authentication);
        service.refresh("refresh");

        var parameters = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(encoder, times(2)).encode(parameters.capture());
        assertThat(parameters.getAllValues()).allSatisfy(value -> {
            assertThat(value.getClaims().getIssuedAt()).isEqualTo(now);
            assertThat(value.getClaims().getExpiresAt()).isEqualTo(now.plus(properties.tokenTtl()));
        });
    }
}
