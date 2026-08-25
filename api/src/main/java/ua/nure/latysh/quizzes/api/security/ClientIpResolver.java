package ua.nure.latysh.quizzes.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
public class ClientIpResolver {
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(SecurityProperties properties) {
        trustedProxies = properties.rateLimit().trustedProxyCidrs().stream()
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (!isTrusted(remoteAddress) || !StringUtils.hasText(forwardedFor)) {
            return remoteAddress;
        }

        String selectedAddress = remoteAddress;
        String[] chain = forwardedFor.split(",", -1);
        for (int index = chain.length - 1; index >= 0; index--) {
            String candidate = chain[index].trim();
            if (!isIpLiteral(candidate)) {
                return remoteAddress;
            }
            selectedAddress = candidate;
            if (!isTrusted(candidate)) {
                return candidate;
            }
        }
        return selectedAddress;
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private static boolean isIpLiteral(String candidate) {
        if (candidate.indexOf(':') >= 0) {
            if (!candidate.matches("[0-9a-fA-F:]+")) {
                return false;
            }
            try {
                return InetAddress.getByName(candidate) instanceof Inet6Address;
            } catch (UnknownHostException _) {
                return false;
            }
        }
        String[] octets = candidate.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            try {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException _) {
                return false;
            }
        }
        return true;
    }
}
