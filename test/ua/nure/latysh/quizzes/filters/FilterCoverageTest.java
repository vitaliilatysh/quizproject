package ua.nure.latysh.quizzes.filters;

import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Locale;
import java.security.SecureRandom;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.Assert.assertNotNull;

public class FilterCoverageTest {

    @Test
    public void authenticationFilterRedirectsGuestsAndConfiguresAuthenticatedRequests() throws Exception {
        AuthenticationFilter filter = new AuthenticationFilter();
        FilterConfig config = mock(FilterConfig.class);
        ServletContext context = mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        filter.init(config);

        HttpServletRequest guestRequest = mock(HttpServletRequest.class);
        HttpServletResponse guestResponse = mock(HttpServletResponse.class);
        FilterChain guestChain = mock(FilterChain.class);
        when(guestRequest.getSession(false)).thenReturn(null);
        when(guestRequest.getContextPath()).thenReturn("/quiz");
        when(guestRequest.getServletPath()).thenReturn("/quizzes");
        filter.doFilter(guestRequest, guestResponse, guestChain);
        verify(guestResponse).sendRedirect("/quiz/");
        verify(guestChain, never()).doFilter(guestRequest, guestResponse);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("role")).thenReturn(2);
        when(request.getServletPath()).thenReturn("/quizzes");
        when(request.getMethod()).thenReturn("GET");
        filter.doFilter(request, response, chain);
        verify(response).setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setDateHeader("Expires", 0);
        verify(request).setCharacterEncoding("UTF-8");
        verify(chain).doFilter(request, response);
    }

    @Test
    public void localeFilterCoversRussianEnglishExistingAndBrowserLocales() throws Exception {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            SessionLocaleFilter filter = new SessionLocaleFilter();
            filter.init(mock(FilterConfig.class));
            runLocaleFilter(filter, "ru", null, Locale.ENGLISH);
            runLocaleFilter(filter, "en", null, Locale.ENGLISH);
            runLocaleFilter(filter, null, Locale.ENGLISH, Locale.ENGLISH);
            runLocaleFilter(filter, null, null, new Locale("ru"));
            filter.destroy();
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void csrfFilterCreatesAndValidatesTokensForSafeAndUnsafeRequests() throws Exception {
        assertNotNull(new CsrfFilter());
        SecureRandom random = mock(SecureRandom.class);
        CsrfFilter filter = new CsrfFilter(random);
        HttpSession session = mock(HttpSession.class);

        HttpServletRequest safeRequest = mock(HttpServletRequest.class);
        HttpServletResponse safeResponse = mock(HttpServletResponse.class);
        FilterChain safeChain = mock(FilterChain.class);
        when(safeRequest.getSession(true)).thenReturn(session);
        when(safeRequest.getMethod()).thenReturn("GET");
        filter.doFilter(safeRequest, safeResponse, safeChain);
        verify(session).setAttribute(org.mockito.ArgumentMatchers.eq(CsrfFilter.SESSION_ATTRIBUTE), anyString());
        verify(safeChain).doFilter(safeRequest, safeResponse);

        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        HttpServletRequest validRequest = mock(HttpServletRequest.class);
        HttpServletResponse validResponse = mock(HttpServletResponse.class);
        FilterChain validChain = mock(FilterChain.class);
        when(validRequest.getSession(true)).thenReturn(session);
        when(session.getAttribute(CsrfFilter.SESSION_ATTRIBUTE)).thenReturn(token);
        when(validRequest.getMethod()).thenReturn("POST");
        when(validRequest.getParameter(CsrfFilter.REQUEST_PARAMETER)).thenReturn(token);
        filter.doFilter(validRequest, validResponse, validChain);
        verify(validChain).doFilter(validRequest, validResponse);

        HttpServletRequest invalidRequest = mock(HttpServletRequest.class);
        HttpServletResponse invalidResponse = mock(HttpServletResponse.class);
        FilterChain invalidChain = mock(FilterChain.class);
        when(invalidRequest.getSession(true)).thenReturn(session);
        when(invalidRequest.getMethod()).thenReturn("POST");
        filter.doFilter(invalidRequest, invalidResponse, invalidChain);
        verify(invalidResponse).sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
        verify(invalidChain, never()).doFilter(invalidRequest, invalidResponse);
    }

    @Test
    public void authenticationFilterCoversPublicRoutesAndTheCompleteRoleMatrix() throws Exception {
        AuthenticationFilter filter = new AuthenticationFilter();
        FilterConfig config = mock(FilterConfig.class);
        when(config.getServletContext()).thenReturn(mock(ServletContext.class));
        filter.init(config);

        String[] publicPaths = {null, "", "/", "/login", "/signup", "/lang", "/static/app.css", "/favicon.ico"};
        for (String path : publicPaths) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            when(request.getServletPath()).thenReturn(path);
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }

        assertAuthorized(filter, "/subjects", "GET", null, 1, true);
        assertAuthorized(filter, "/users", "GET", null, 2, false);
        assertAuthorized(filter, "/allresults", "GET", null, 1, true);
        assertAuthorized(filter, "/results", "GET", null, 2, true);
        assertAuthorized(filter, "/results", "GET", null, 1, false);
        assertAuthorized(filter, "/quizzes", "GET", null, 2, true);
        assertAuthorized(filter, "/quizzes", "GET", null, 1, true);
        assertAuthorized(filter, "/quizzes", "POST", "search", 2, true);
        assertAuthorized(filter, "/quizzes", "POST", "update", 1, true);
        assertAuthorized(filter, "/quizzes", "POST", "update", 2, false);
        assertAuthorized(filter, "/quizzes", "POST", "unknown", 1, false);
        assertAuthorized(filter, "/questions", "GET", null, 1, true);
        assertAuthorized(filter, "/questions", "GET", null, 2, true);
        assertAuthorized(filter, "/questions", "POST", "run", 2, true);
        assertAuthorized(filter, "/questions", "POST", "run", 1, false);
        assertAuthorized(filter, "/questions", "POST", "editQuestion", 1, true);
        assertAuthorized(filter, "/questions", "POST", "unknown", 1, false);
        assertAuthorized(filter, "/profile", "GET", null, 1, true);
        assertAuthorized(filter, "/logout", "POST", null, 2, true);
        assertAuthorized(filter, "/index.jsp", "GET", null, 1, false);

        HttpServletRequest malformed = mock(HttpServletRequest.class);
        HttpServletResponse malformedResponse = mock(HttpServletResponse.class);
        when(malformed.getServletPath()).thenReturn("/quizzes");
        when(malformed.getContextPath()).thenReturn("/quiz");
        HttpSession malformedSession = mock(HttpSession.class);
        when(malformed.getSession(false)).thenReturn(malformedSession);
        when(malformedSession.getAttribute("role")).thenReturn("admin");
        filter.doFilter(malformed, malformedResponse, mock(FilterChain.class));
        verify(malformedResponse).sendRedirect("/quiz/");
    }

    private void assertAuthorized(AuthenticationFilter filter, String path, String method,
                                  String action, int role, boolean allowed) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getServletPath()).thenReturn(path);
        when(request.getMethod()).thenReturn(method);
        when(request.getParameter("action")).thenReturn(action);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("role")).thenReturn(role);
        filter.doFilter(request, response, chain);
        if (allowed) {
            verify(chain).doFilter(request, response);
        } else {
            verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    private void runLocaleFilter(SessionLocaleFilter filter,
                                 String parameter,
                                 Locale storedLocale,
                                 Locale browserLocale) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getParameter("lang")).thenReturn(parameter);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("lang")).thenReturn(storedLocale);
        when(request.getLocale()).thenReturn(browserLocale);

        filter.doFilter(request, response, chain);

        verify(request).setCharacterEncoding("UTF-8");
        verify(chain).doFilter(request, response);
    }
}
