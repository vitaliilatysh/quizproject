package ua.nure.latysh.quizzes.servlets;

import org.junit.Test;
import ua.nure.latysh.quizzes.services.LevelService;
import ua.nure.latysh.quizzes.services.QuizService;
import ua.nure.latysh.quizzes.services.SubjectService;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuizServletTest {

    @Test
    public void postWithoutActionFallsBackToListPage() throws Exception {
        QuizService quizService = mock(QuizService.class);
        QuizServlet servlet = new QuizServlet(
                quizService, mock(SubjectService.class), mock(LevelService.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);

        when(request.getParameter("action")).thenReturn(null);
        when(request.getRequestDispatcher("/WEB-INF/views/quizzes.jsp")).thenReturn(dispatcher);
        when(quizService.getAllQuizzes()).thenReturn(Collections.emptyList());

        servlet.doPost(request, response);

        verify(request).setAttribute("quizzes", Collections.emptyList());
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void unknownActionReturnsBadRequest() throws Exception {
        QuizServlet servlet = new QuizServlet(
                mock(QuizService.class), mock(SubjectService.class), mock(LevelService.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getParameter("action")).thenReturn("unsupported");

        servlet.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action: unsupported");
    }
}
