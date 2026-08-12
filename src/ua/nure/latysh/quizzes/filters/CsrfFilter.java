package ua.nure.latysh.quizzes.filters;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@WebFilter(filterName = "CsrfFilter", urlPatterns = {"/*"})
public class CsrfFilter implements Filter {
    public static final String SESSION_ATTRIBUTE = "csrfToken";
    public static final String REQUEST_PARAMETER = "_csrf";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final SecureRandom secureRandom;

    public CsrfFilter() {
        this(new SecureRandom());
    }

    CsrfFilter(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(true);
        String expectedToken = (String) session.getAttribute(SESSION_ATTRIBUTE);

        if (expectedToken == null) {
            expectedToken = createToken();
            session.setAttribute(SESSION_ATTRIBUTE, expectedToken);
        }

        if (!SAFE_METHODS.contains(httpRequest.getMethod())) {
            String actualToken = httpRequest.getParameter(REQUEST_PARAMETER);
            if (!matches(expectedToken, actualToken)) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String createToken() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private boolean matches(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
