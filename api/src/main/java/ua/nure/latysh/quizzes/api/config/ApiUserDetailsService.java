package ua.nure.latysh.quizzes.api.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ua.nure.latysh.quizzes.api.domain.UserRepository;

import java.util.Locale;

@Service
public class ApiUserDetailsService implements UserDetailsService {
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

    private static String toAuthority(String role) {
        String normalized = role.toUpperCase(Locale.ROOT);
        return "ROLE_" + ("STUDENT".equals(normalized) ? "USER" : normalized);
    }
}
