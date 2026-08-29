package ua.nure.latysh.quizzes.api.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.domain.UserRepository;

import java.util.Locale;

@Service
public class ApiUserDetailsService implements UserDetailsService, UserDetailsPasswordService {
    private final UserRepository userRepository;

    public ApiUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = userRepository.findByLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return User.withUsername(user.getLogin())
                .password(user.getPassword())
                .disabled(!"active".equalsIgnoreCase(user.getStatus().getName()))
                .authorities(new SimpleGrantedAuthority(toAuthority(user.getRole().getName())))
                .build();
    }

    /**
     * Persists a re-encoded password. Spring Security calls this after a
     * successful login when the encoder reports the stored value is in an old
     * format, which is how plain-text passwords inherited from the legacy
     * schema get replaced by PBKDF2 hashes.
     *
     * <p>Implementing this interface is what makes the upgrade happen at all:
     * DaoAuthenticationProvider consults the encoder either way, but only
     * rewrites the password when a UserDetailsPasswordService bean exists.
     */
    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        userRepository.findByLogin(user.getUsername())
                .ifPresent(account -> account.setPassword(newPassword));
        return User.withUserDetails(user).password(newPassword).build();
    }

    private static String toAuthority(String role) {
        String normalized = role.toUpperCase(Locale.ROOT);
        return "ROLE_" + ("STUDENT".equals(normalized) ? "USER" : normalized);
    }
}
