package ua.nure.latysh.quizzes.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.admin.AdminModels.UserResponse;
import ua.nure.latysh.quizzes.api.auth.RefreshSessionService;
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
    private final RefreshSessionService refreshSessions;

    public UserAdminService(
            UserRepository userRepository,
            StatusRepository statusRepository,
            RefreshSessionService refreshSessions) {
        this.userRepository = userRepository;
        this.statusRepository = statusRepository;
        this.refreshSessions = refreshSessions;
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
        if (BLOCKED.equals(normalizedStatus)) {
            refreshSessions.revokeAll(user.getLogin());
        }
        return toResponse(user);
    }

    /**
     * Whether blocking this account would leave nobody able to administer the
     * system.
     *
     * <p>Refusing to block the caller's own account is not enough: two active
     * administrators could block each other concurrently. Refresh sessions now
     * make the first block effective immediately, but serialising the decision
     * still prevents both requests from observing two administrators and
     * committing a state with none.
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
