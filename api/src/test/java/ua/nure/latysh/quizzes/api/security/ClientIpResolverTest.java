package ua.nure.latysh.quizzes.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import ua.nure.latysh.quizzes.api.config.SecurityProperties;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ua.nure.latysh.quizzes.api.config.SecurityProperties.RateLimitProperties.Backend.MEMORY;

class ClientIpResolverTest {
    private final ClientIpResolver resolver = new ClientIpResolver(properties());

    @Test
    void ignoresForwardedHeadersFromAnUntrustedPeer() {
        assertEquals("198.51.100.8", resolve("198.51.100.8", "203.0.113.9"));
        assertEquals("198.51.100.8", resolve("198.51.100.8", null));
    }

    @Test
    void walksAForwardedChainFromTheTrustedProxyToTheClient() {
        assertEquals("203.0.113.9", resolve("10.0.0.4", "203.0.113.9, 10.0.0.3"));
        assertEquals("2001:db8::9", resolve("10.0.0.4", "2001:db8::9"));
        assertEquals("10.0.0.2", resolve("10.0.0.4", "10.0.0.2, 10.0.0.3"));
    }

    @Test
    void fallsBackToTheDirectPeerForMalformedForwardedChains() {
        assertEquals("10.0.0.4", resolve("10.0.0.4", "unknown"));
        assertEquals("10.0.0.4", resolve("10.0.0.4", "999.1.1.1"));
        assertEquals("10.0.0.4", resolve("10.0.0.4", "-1.1.1.1"));
        assertEquals("10.0.0.4", resolve("10.0.0.4", "one.1.1.1"));
        assertEquals("10.0.0.4", resolve("10.0.0.4", "1.2.3"));
        assertEquals("10.0.0.4", resolve("10.0.0.4", "gg::1"));
        assertEquals("10.0.0.4", resolve("10.0.0.4", ":::"));
        assertEquals("10.0.0.4", resolve("10.0.0.4", ""));
    }

    private String resolve(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return resolver.resolve(request);
    }

    private static SecurityProperties properties() {
        return new SecurityProperties("secret", "issuer", Duration.ofMinutes(15), List.of("https://example.test"),
                new SecurityProperties.RateLimitProperties(
                        MEMORY, 100, 3, Duration.ofMinutes(1), 100,
                        List.of("10.0.0.0/8", "127.0.0.1/32")));
    }
}
