package ua.nure.latysh.quizzes.servlets;

import org.junit.Test;
import ua.nure.latysh.quizzes.services.UserService;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LoginServletTest {

    @Test
    public void blankCredentialsAreRejectedWithoutCallingRepositoryOrExposingPassword() throws Exception {
        UserService userService = mock(UserService.class);
        LoginServlet servlet = new LoginServlet(userService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);

        when(request.getParameter("username")).thenReturn("   ");
        when(request.getParameter("password")).thenReturn("secret");
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("lang")).thenReturn(Locale.ENGLISH);
        when(request.getRequestDispatcher("/")).thenReturn(dispatcher);

        servlet.doPost(request, response);

        verify(userService, never()).findByLoginAndPassword(any(), any());
        verify(request, never()).setAttribute(eq("password"), any());
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void missingUsernameIsRejectedBeforeAuthentication() throws Exception {
        UserService userService = mock(UserService.class);
        LoginServlet servlet = new LoginServlet(userService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getParameter("password")).thenReturn("secret");
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("lang")).thenReturn(Locale.ENGLISH);
        when(request.getRequestDispatcher("/")).thenReturn(dispatcher);

        servlet.doPost(request, response);

        verify(userService, never()).findByLoginAndPassword(any(), any());
        verify(request).setAttribute("username", null);
        verify(dispatcher).forward(request, response);
    }
}
