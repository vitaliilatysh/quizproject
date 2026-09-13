package ua.nure.latysh.quizzes.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ua.nure.latysh.quizzes.api.auth.RefreshSessionService;
import ua.nure.latysh.quizzes.api.domain.Role;
import ua.nure.latysh.quizzes.api.domain.Status;
import ua.nure.latysh.quizzes.api.domain.StatusRepository;
import ua.nure.latysh.quizzes.api.domain.UserAccount;
import ua.nure.latysh.quizzes.api.domain.UserRepository;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The account paths a correctly seeded database cannot reach.
 *
 * <p>The contract test drives this service through MockMvc against the schema
 * the migrations build, which is the right way to test what it does. What it
 * cannot do is take the branches that only open when the data is not what the
 * schema promises. Those decide whether the API answers with a diagnosable
 * error or a NullPointerException.
 */
class UserAdminServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final StatusRepository statusRepository = mock(StatusRepository.class);
    private final RefreshSessionService refreshSessions = mock(RefreshSessionService.class);

    private final UserAdminService service =
            new UserAdminService(userRepository, statusRepository, refreshSessions);

    @Test
    void refusesAStatusChangeToAStatusTheDatabaseDoesNotHave() {
        when(userRepository.findById(3)).thenReturn(Optional.of(user("olena")));
        when(statusRepository.findByNameIgnoreCase("active")).thenReturn(Optional.empty());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.updateUserStatus(3, "ACTIVE", "admin"));

        assertEquals("Status 'active' is not configured", failure.getMessage());
    }

    @Test
    void refusesToBlockTheLastActiveAdministrator() {
        UserAccount administrator = withId(user("sole-admin"), 5);
        when(userRepository.findById(5)).thenReturn(Optional.of(administrator));
        when(userRepository.lockActiveAdministrators()).thenReturn(List.of(administrator));

        ResourceConflictException failure = assertThrows(ResourceConflictException.class,
                () -> service.updateUserStatus(5, "blocked", "another-admin"));

        assertEquals("Blocking user 5 would leave no active administrator", failure.getMessage());
    }

    private static UserAccount user(String login) {
        var account = new UserAccount();
        account.setLogin(login);
        account.setStatus(new Status());
        account.setRole(new Role());
        return account;
    }

    private static <T> T withId(T entity, int id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
