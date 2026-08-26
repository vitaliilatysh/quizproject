package ua.nure.latysh.quizzes.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import ua.nure.latysh.quizzes.api.security.JsonAccessDeniedHandler;
import ua.nure.latysh.quizzes.api.security.JsonAuthenticationEntryPoint;
import ua.nure.latysh.quizzes.api.security.RateLimitFilter;
import ua.nure.latysh.quizzes.api.observability.CorrelationIdFilter;

import java.util.List;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {
    private static final String ADMIN_ROLE = "ADMIN";

    @Bean
    @SuppressWarnings("java:S4502") // Bearer-only auth never relies on automatically submitted cookies.
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            CorrelationIdFilter correlationIdFilter,
            RateLimitFilter rateLimitFilter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register",
                                "/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus",
                                "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole(ADMIN_ROLE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/quizzes/**").permitAll()
                        .requestMatchers("/api/v1/auth/refresh",
                                "/api/v1/results/me", "/api/v1/users/me", "/api/v1/users/me/**",
                                "/api/v1/attempts/**",
                                "/api/v1/quizzes/*/attempts").hasAnyRole("USER", ADMIN_ROLE)
                        .requestMatchers("/api/v1/admin/**").hasRole(ADMIN_ROLE)
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterBefore(correlationIdFilter, CorsFilter.class)
                .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type",
                CorrelationIdFilter.HEADER_NAME));
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER_NAME,
                "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}

