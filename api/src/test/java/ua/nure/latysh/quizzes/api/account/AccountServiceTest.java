package ua.nure.latysh.quizzes.api.account;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import ua.nure.latysh.quizzes.api.auth.RefreshSessionService;
import ua.nure.latysh.quizzes.api.auth.RegisterRequest;
import ua.nure.latysh.quizzes.api.domain.Role;
import ua.nure.latysh.quizzes.api.domain.RoleRepository;
import ua.nure.latysh.quizzes.api.domain.Status;
import ua.nure.latysh.quizzes.api.domain.StatusRepository;
import ua.nure.latysh.quizzes.api.domain.UserRepository;
import ua.nure.latysh.quizzes.api.support.ResourceNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What registration does when the database is not seeded the way it must be.
 *
 * Every account is created with the 'active' status and the 'student' role,
 * both looked up by name from rows the migrations insert. The contract test
 * runs against a correctly seeded schema, so it can never take these two
 * branches — and they are the ones that decide whether a half-built account
 * reaches the users table.
 */
class AccountServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final StatusRepository statusRepository = mock(StatusRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RefreshSessionService refreshSessions = mock(RefreshSessionService.class);
    private final AccountService service =
            new AccountService(userRepository, roleRepository, statusRepository, passwordEncoder, refreshSessions);

    private static final RegisterRequest REQUEST =
            new RegisterRequest("olena", "Olena", "Kovalchuk", "Password1!");

    @Test
    void refusesToRegisterWhenTheActiveStatusIsMissing() {
        when(statusRepository.findByNameIgnoreCase("active")).thenReturn(Optional.empty());

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> service.register(REQUEST));

        assertEquals("Status 'active' is not configured", failure.getMessage());
        // The account must not be written half-built: an account with no status
        // cannot sign in and cannot be repaired through the API either.
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToRegisterWhenTheStudentRoleIsMissing() {
        when(statusRepository.findByNameIgnoreCase("active")).thenReturn(Optional.of(new Status()));
        when(roleRepository.findByNameIgnoreCase("student")).thenReturn(Optional.empty());

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> service.register(REQUEST));

        assertEquals("Role 'student' is not configured", failure.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registersWhenBothSeedRowsAreThere() {
        when(statusRepository.findByNameIgnoreCase("active")).thenReturn(Optional.of(new Status()));
        when(roleRepository.findByNameIgnoreCase("student")).thenReturn(Optional.of(new Role()));
        when(passwordEncoder.encode("Password1!")).thenReturn("encoded");

        service.register(REQUEST);

        verify(userRepository).saveAndFlush(any());
    }

    @Test
    void reportsWhenTheCurrentUserDisappeared() {
        when(userRepository.findByLogin("missing")).thenReturn(Optional.empty());
        ChangePasswordRequest request = new ChangePasswordRequest("CurrentPass1", "ReplacementPass1");

        ResourceNotFoundException profileFailure = assertThrows(
                ResourceNotFoundException.class, () -> service.profile("missing"));
        ResourceNotFoundException passwordFailure = assertThrows(
                ResourceNotFoundException.class,
                () -> service.changePassword("missing", request));

        assertEquals("Current user was not found", profileFailure.getMessage());
        assertEquals("Current user was not found", passwordFailure.getMessage());
    }
}
