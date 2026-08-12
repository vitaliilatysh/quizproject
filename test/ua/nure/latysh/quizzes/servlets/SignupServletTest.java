package ua.nure.latysh.quizzes.servlets;

import org.junit.Test;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.services.UserService;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Locale;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertThrows;

public class SignupServletTest {

    @Test
    public void passwordsAreCaseSensitiveAndAreNotReturnedToTheView() throws Exception {
        UserService userService = mock(UserService.class);
        SignupServlet servlet = new SignupServlet(userService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);

        when(request.getParameter("username")).thenReturn("student");
        when(request.getParameter("firstName")).thenReturn("Test");
        when(request.getParameter("lastName")).thenReturn("User");
        when(request.getParameter("password")).thenReturn("Secret12");
        when(request.getParameter("confirmPassword")).thenReturn("secret12");
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("lang")).thenReturn(Locale.ENGLISH);
        when(userService.findUserByLogin("student")).thenReturn(Optional.empty());
        when(request.getRequestDispatcher("/WEB-INF/views/signup.jsp")).thenReturn(dispatcher);

        servlet.doPost(request, response);

        verify(userService, never()).save(any(User.class));
        verify(request, never()).setAttribute(eq("password"), any());
        verify(request, never()).setAttribute(eq("confirmPassword"), any());
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void savedUserMustBeReloadable() {
        UserService userService = mock(UserService.class);
        SignupServlet servlet = new SignupServlet(userService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getParameter("username")).thenReturn("student");
        when(request.getParameter("firstName")).thenReturn("Test");
        when(request.getParameter("lastName")).thenReturn("User");
        when(request.getParameter("password")).thenReturn("Secret12");
        when(request.getParameter("confirmPassword")).thenReturn("Secret12");
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("lang")).thenReturn(Locale.ENGLISH);
        when(userService.findUserByLogin("student")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> servlet.doPost(request, response));
    }
}
