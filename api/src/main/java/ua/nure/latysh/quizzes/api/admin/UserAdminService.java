package ua.nure.latysh.quizzes.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.admin.AdminModels.UserResponse;
import ua.nure.latysh.quizzes.api.domain.Status;
import ua.nure.latysh.quizzes.api.domain.StatusRepository;
import ua.nure.latysh.quizzes.api.domain.UserAccount;
import ua.nure.latysh.quizzes.api.domain.UserRepository;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;

import java.util.List;
import java.util.Locale;

/**
 * Accounts, as an administrator sees them: who exists and who is blocked.
 *
 * <p>See the package documentation for transactions.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class UserAdminService {
    private static final String BLOCKED = "blocked";

    private final UserRepository userRepository;
    private final StatusRepository statusRepository;

    public UserAdminService(UserRepository userRepository, StatusRepository statusRepository) {
        this.userRepository = userRepository;
        this.statusRepository = statusRepository;
    }

    public Page<UserResponse> users(Pageable pageable) {
        return userRepository.findAllByOrderByLoginAsc(pageable).map(UserAdminService::toResponse);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public UserResponse updateUserStatus(int userId, String status, String currentUsername) {
        String normalizedStatus = status.toLowerCase(Locale.ROOT);
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> AdminErrors.missing("User", userId));
        if (user.getLogin().equals(currentUsername) && BLOCKED.equals(normalizedStatus)) {
            throw new ResourceConflictException("An administrator cannot block the current account");
        }
        if (BLOCKED.equals(normalizedStatus) && isLastActiveAdministrator(userId)) {
            throw new ResourceConflictException(
                    "Blocking user " + userId + " would leave no active administrator");
        }
        user.setStatus(requireStatus(normalizedStatus));
        return toResponse(user);
    }

    /**
     * Whether blocking this account would leave nobody able to administer the
     * system.
     *
     * <p>Refusing to block the caller's own account is not enough. The API is a
     * stateless JWT resource server: authorities come from the token's claims
     * and the account is not re-read per request, so blocking someone does not
     * revoke the token they already hold. For the length of its time to live a
     * blocked administrator keeps full rights, which is long enough to block
     * the administrator who just blocked them. That leaves an installation with
     * no active administrator and no way back, because unblocking anyone needs
     * the role nobody holds any more.
     *
     * <p>"For the length of its time to live" is true only because
     * {@code /api/v1/auth/refresh} re-reads the account before issuing a new
     * token. It used to issue one from the presented token's own claims, which
     * made the window unbounded: a blocked account refreshed itself for ever.
     * A change there that stops re-reading brings this hole back, and this
     * guard is the last thing standing between it and an installation nobody
     * can administer.
     */
    private boolean isLastActiveAdministrator(int userId) {
        List<UserAccount> activeAdministrators = userRepository.lockActiveAdministrators();
        return activeAdministrators.size() == 1
                && activeAdministrators.getFirst().getId() == userId;
    }

    private Status requireStatus(String normalizedStatus) {
        return statusRepository.findByNameIgnoreCase(normalizedStatus)
                .orElseThrow(() -> new IllegalStateException("Status '" + normalizedStatus + "' is not configured"));
    }

    private static UserResponse toResponse(UserAccount user) {
        return new UserResponse(
                user.getId(), user.getLogin(), user.getRole().getName(), user.getStatus().getName());
    }
}
