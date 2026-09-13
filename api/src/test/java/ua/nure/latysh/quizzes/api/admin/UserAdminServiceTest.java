package ua.nure.latysh.quizzes.api.admin;

import org.junit.jupiter.api.Test;
import ua.nure.latysh.quizzes.api.domain.Role;
import ua.nure.latysh.quizzes.api.domain.Status;
import ua.nure.latysh.quizzes.api.domain.StatusRepository;
import ua.nure.latysh.quizzes.api.domain.UserAccount;
import ua.nure.latysh.quizzes.api.domain.UserRepository;

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

    private final UserAdminService service = new UserAdminService(userRepository, statusRepository);

    @Test
    void refusesAStatusChangeToAStatusTheDatabaseDoesNotHave() {
        when(userRepository.findById(3)).thenReturn(Optional.of(user("olena")));
        when(statusRepository.findByNameIgnoreCase("active")).thenReturn(Optional.empty());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.updateUserStatus(3, "ACTIVE", "admin"));

        assertEquals("Status 'active' is not configured", failure.getMessage());
    }

    private static UserAccount user(String login) {
        var account = new UserAccount();
        account.setLogin(login);
        account.setStatus(new Status());
        account.setRole(new Role());
        return account;
    }
}
