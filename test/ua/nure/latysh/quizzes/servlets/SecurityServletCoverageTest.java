package ua.nure.latysh.quizzes.servlets;

import org.junit.Test;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.exceptions.QuizSubmissionException;
import ua.nure.latysh.quizzes.security.LoginAttemptLimiter;
import ua.nure.latysh.quizzes.services.AttemptService;
import ua.nure.latysh.quizzes.services.ResultService;
import ua.nure.latysh.quizzes.services.UserService;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SecurityServletCoverageTest {

    @Test
    public void loginReturnsTooManyRequestsWhileTheClientIsBlocked() throws Exception {
        UserService userService = mock(UserService.class);
        LoginAttemptLimiter limiter = mock(LoginAttemptLimiter.class);
        LoginServlet servlet = new LoginServlet(userService, limiter);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getParameter("username")).thenReturn(" Alice ");
        when(request.getParameter("password")).thenReturn("strong-password");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("lang")).thenReturn(Locale.ENGLISH);
        when(request.getRequestDispatcher("/")).thenReturn(dispatcher);
        when(limiter.isBlocked("127.0.0.1:alice")).thenReturn(true);

        servlet.doPost(request, response);

        verify(response).setStatus(429);
        verify(userService, never()).findByLoginAndPassword(anyString(), anyString());
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void resultSubmissionRejectsMissingMalformedInvalidAndExpiredAttempts() throws Exception {
        ResultService resultService = mock(ResultService.class);
        AttemptService attemptService = mock(AttemptService.class);
        ResultServlet servlet = new ResultServlet(resultService, attemptService);

        HttpServletRequest missingRequest = mock(HttpServletRequest.class);
        HttpServletResponse missingResponse = mock(HttpServletResponse.class);
        when(missingRequest.getSession(false)).thenReturn(null);
        servlet.doPost(missingRequest, missingResponse);
        verify(missingResponse).sendError(HttpServletResponse.SC_CONFLICT, "No active attempt");

        User user = new User();
        user.setId(8);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("attemptId")).thenReturn(9);
        when(session.getAttribute("user")).thenReturn(user);

        HttpServletRequest malformedRequest = requestWithSession(session);
        HttpServletResponse malformedResponse = mock(HttpServletResponse.class);
        when(malformedRequest.getParameterValues("answerId")).thenReturn(new String[]{"not-a-number"});
        servlet.doPost(malformedRequest, malformedResponse);
        verify(malformedResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid answer id");

        QuizSubmissionException invalid = new QuizSubmissionException(
                QuizSubmissionException.Reason.INVALID_ANSWER, "foreign answer");
        doThrow(invalid).when(attemptService).completeAttempt(9, 8, Set.of(11));
        HttpServletRequest invalidRequest = requestWithSession(session);
        HttpServletResponse invalidResponse = mock(HttpServletResponse.class);
        when(invalidRequest.getParameterValues("answerId")).thenReturn(new String[]{"11"});
        servlet.doPost(invalidRequest, invalidResponse);
        assertEquals(QuizSubmissionException.Reason.INVALID_ANSWER, invalid.getReason());
        verify(invalidResponse).sendError(HttpServletResponse.SC_BAD_REQUEST, "foreign answer");

        QuizSubmissionException expired = new QuizSubmissionException(
                QuizSubmissionException.Reason.EXPIRED, "expired");
        doThrow(expired).when(attemptService).completeAttempt(9, 8, Set.of(12));
        HttpServletRequest expiredRequest = requestWithSession(session);
        HttpServletResponse expiredResponse = mock(HttpServletResponse.class);
        when(expiredRequest.getParameterValues("answerId")).thenReturn(new String[]{"12"});
        servlet.doPost(expiredRequest, expiredResponse);
        verify(expiredResponse).sendError(HttpServletResponse.SC_CONFLICT, "expired");

        HttpSession noUserSession = mock(HttpSession.class);
        when(noUserSession.getAttribute("attemptId")).thenReturn(9);
        HttpServletRequest noUserRequest = requestWithSession(noUserSession);
        HttpServletResponse noUserResponse = mock(HttpServletResponse.class);
        servlet.doPost(noUserRequest, noUserResponse);
        verify(noUserResponse).sendError(HttpServletResponse.SC_CONFLICT, "No active attempt");
    }

    @Test
    public void resultSubmissionAllowsAnEmptyAnswerSetAndClearsAttemptState() throws Exception {
        ResultServlet servlet = new ResultServlet(mock(ResultService.class), mock(AttemptService.class));
        HttpSession session = mock(HttpSession.class);
        User user = new User();
        user.setId(3);
        when(session.getAttribute("attemptId")).thenReturn(4);
        when(session.getAttribute("user")).thenReturn(user);
        HttpServletRequest request = requestWithSession(session);
        HttpServletResponse response = mock(HttpServletResponse.class);

        servlet.doPost(request, response);

        verify(session).removeAttribute("attemptId");
        verify(session).removeAttribute("quizId");
        verify(session).removeAttribute("quizTime");
        verify(session).removeAttribute("quizExpiresAt");
        verify(session).removeAttribute("questions");
        verify(session).removeAttribute("answersPerQuestion");
        verify(response).sendRedirect("quizzes");
    }

    private static HttpServletRequest requestWithSession(HttpSession session) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(session);
        return request;
    }
}
