package ua.nure.latysh.quizzes.api.account;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.auth.RegisterRequest;
import ua.nure.latysh.quizzes.api.domain.RoleRepository;
import ua.nure.latysh.quizzes.api.domain.StatusRepository;
import ua.nure.latysh.quizzes.api.domain.UserAccount;
import ua.nure.latysh.quizzes.api.domain.UserRepository;
import ua.nure.latysh.quizzes.api.support.InvalidRequestException;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.time.Clock;
import java.time.Instant;

@Service
public class AccountService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StatusRepository statusRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StatusRepository statusRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.statusRepository = statusRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public void register(RegisterRequest request) {
        Instant now = Instant.now(clock);
        var user = new UserAccount();
        user.setLogin(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRegisterDate(now);
        user.setLoginDate(now);
        user.setStatus(statusRepository.findByNameIgnoreCase("active")
                .orElseThrow(() -> new IllegalStateException("Status 'active' is not configured")));
        user.setRole(roleRepository.findByNameIgnoreCase("student")
                .orElseThrow(() -> new IllegalStateException("Role 'student' is not configured")));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("Username is already registered", exception);
        }
    }

    @Transactional
    public void recordLogin(String username) {
        userRepository.findByLogin(username)
                .ifPresent(user -> user.setLoginDate(Instant.now(clock)));
    }

    public ProfileResponse profile(String username) {
        var user = userRepository.findByLogin(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user was not found"));
        return new ProfileResponse(
                user.getLogin(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().getName(),
                user.getStatus().getName(),
                user.getRegisterDate(),
                user.getLoginDate());
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        var user = userRepository.findByLogin(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user was not found"));
        String encodedPassword = user.getPassword();
        if (!passwordEncoder.matches(request.currentPassword(), encodedPassword)) {
            throw new InvalidRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), encodedPassword)) {
            throw new ResourceConflictException("New password must differ from the current password");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }
}
