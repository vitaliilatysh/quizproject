package ua.nure.latysh.quizzes.filters;

import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Locale;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        filter.doFilter(guestRequest, guestResponse, guestChain);
        verify(guestResponse).sendRedirect("/quiz/");
        verify(guestChain, never()).doFilter(guestRequest, guestResponse);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("role")).thenReturn(2);
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
