package ua.nure.latysh.quizzes.api.config;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ApiUserDetailsService implements UserDetailsService {
    private final JdbcClient jdbcClient;

    public ApiUserDetailsService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return jdbcClient.sql("""
                        SELECT users.login, users.password, roles.name AS role_name, statuses.name AS status_name
                        FROM users
                        JOIN roles ON roles.id = users.role_id
                        JOIN statuses ON statuses.id = users.status_id
                        WHERE users.login = :username
                        """)
                .param("username", username)
                .query((resultSet, rowNumber) -> User.withUsername(resultSet.getString("login"))
                        .password(resultSet.getString("password"))
                        .disabled(!"active".equalsIgnoreCase(resultSet.getString("status_name")))
                        .authorities(new SimpleGrantedAuthority(toAuthority(resultSet.getString("role_name"))))
                        .build())
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    private static String toAuthority(String role) {
        String normalized = role.toUpperCase(Locale.ROOT);
        return "ROLE_" + ("STUDENT".equals(normalized) ? "USER" : normalized);
    }
}
