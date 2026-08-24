package ua.nure.latysh.quizzes.api.account;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.auth.RegisterRequest;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

@Service
public class AccountService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this(jdbcTemplate, passwordEncoder, Clock.systemUTC());
    }

    AccountService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void register(RegisterRequest request) {
        Instant now = Instant.now(clock);
        try {
            jdbcTemplate.update("""
                    INSERT INTO users (
                        login, password, first_name, last_name, register_date, login_date, status_id, role_id
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?,
                        (SELECT id FROM statuses WHERE LOWER(name) = 'active'),
                        (SELECT id FROM roles WHERE LOWER(name) = 'student')
                    )
                    """,
                    request.username(), passwordEncoder.encode(request.password()),
                    request.firstName(), request.lastName(), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new ResourceConflictException("Username is already registered", exception);
        }
    }

    public void recordLogin(String username) {
        jdbcTemplate.update("UPDATE users SET login_date = ? WHERE login = ?",
                Timestamp.from(Instant.now(clock)), username);
    }

    public ProfileResponse profile(String username) {
        return jdbcTemplate.query("""
                        SELECT users.login, users.first_name, users.last_name,
                               roles.name AS role_name, statuses.name AS status_name,
                               users.register_date, users.login_date
                        FROM users
                        JOIN roles ON roles.id = users.role_id
                        JOIN statuses ON statuses.id = users.status_id
                        WHERE users.login = ?
                        """,
                (resultSet, rowNumber) -> new ProfileResponse(
                        resultSet.getString("login"),
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name"),
                        resultSet.getString("role_name"),
                        resultSet.getString("status_name"),
                        resultSet.getTimestamp("register_date").toInstant(),
                        resultSet.getTimestamp("login_date").toInstant()),
                username).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Current user was not found"));
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        String encodedPassword = jdbcTemplate.query(
                        "SELECT password FROM users WHERE login = ?",
                        (resultSet, rowNumber) -> resultSet.getString("password"), username)
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Current user was not found"));
        if (!passwordEncoder.matches(request.currentPassword(), encodedPassword)) {
            throw new InvalidRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), encodedPassword)) {
            throw new ResourceConflictException("New password must differ from the current password");
        }
        jdbcTemplate.update("UPDATE users SET password = ? WHERE login = ?",
                passwordEncoder.encode(request.newPassword()), username);
    }
}
