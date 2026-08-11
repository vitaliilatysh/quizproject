package ua.nure.latysh.quizzes.servlets;

import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogoutServletTest {

    @Test
    public void logoutWithoutSessionRedirectsToApplicationRootWithoutCreatingSession() throws Exception {
        LogoutServlet servlet = new LogoutServlet();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getSession(false)).thenReturn(null);
        when(request.getContextPath()).thenReturn("/quizproject");

        servlet.doPost(request, response);

        verify(request, never()).getSession();
        verify(response).sendRedirect("/quizproject/");
    }
}
