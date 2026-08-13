package ua.nure.latysh.quizzes.servlets;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import ua.nure.latysh.quizzes.dto.QuizDto;
import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.services.LevelService;
import ua.nure.latysh.quizzes.services.QuizService;
import ua.nure.latysh.quizzes.services.SubjectService;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuizServletCoverageTest {
    private static Locale originalLocale;

    @BeforeClass
    public static void useEnglishBundle() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterClass
    public static void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    public void getAddDeleteEditAndSearchActionsAreCovered() throws Exception {
        QuizService quizService = mock(QuizService.class);
        SubjectService subjectService = mock(SubjectService.class);
        LevelService levelService = mock(LevelService.class);
        QuizServlet servlet = new QuizServlet(quizService, subjectService, levelService);
        QuizDto dto = new QuizDto();
        Subject subject = new Subject();
        Level level = new Level();
        Quiz quiz = quiz(4, "Quiz");
        when(quizService.getAllQuizzes()).thenReturn(List.of(dto));
        when(subjectService.getAllSubjects()).thenReturn(List.of(subject));
        when(levelService.findAllLevels()).thenReturn(List.of(level));
        when(quizService.findQuizById(4)).thenReturn(Optional.of(quiz));
        when(quizService.findQuizBySubjectName("Java")).thenReturn(List.of(dto));

        WebContext get = context();
        servlet.doGet(get.request, get.response);
        verify(get.request).setAttribute("quizzes", List.of(dto));
        verify(get.dispatcher).forward(get.request, get.response);

        WebContext add = action("add");
        servlet.doPost(add.request, add.response);
        verify(add.request).setAttribute("complexities", List.of(level));
        verify(add.request).setAttribute("subjects", List.of(subject));
        verify(add.dispatcher).forward(add.request, add.response);

        WebContext delete = action("delete");
        when(delete.request.getParameter("quiz")).thenReturn("4");
        servlet.doPost(delete.request, delete.response);
        verify(quizService).deleteQuiz(quiz);
        verify(delete.response).sendRedirect("quizzes");

        WebContext edit = action("edit");
        when(edit.request.getParameter("quiz")).thenReturn("4");
        when(edit.request.getParameter("quizName")).thenReturn(" Quiz ");
        when(edit.request.getParameter("quizSubject")).thenReturn("Java");
        when(edit.request.getParameter("quizComplexity")).thenReturn("easy");
        when(edit.request.getParameter("quizTime")).thenReturn("10");
        servlet.doPost(edit.request, edit.response);
        verify(edit.request).setAttribute("quiz", 4);
        verify(edit.request).setAttribute("quizName", "Quiz");
        verify(edit.request).setAttribute("quizTime", 10);
        verify(edit.dispatcher).forward(edit.request, edit.response);

        WebContext search = action("search");
        when(search.request.getParameter("subject")).thenReturn(" Java ");
        servlet.doPost(search.request, search.response);
        verify(search.request).setAttribute("quizzes", List.of(dto));
        verify(search.request).setAttribute("subjectName", "Java");
        verify(search.dispatcher).forward(search.request, search.response);
    }

    @Test
    public void createAndUpdateDelegateSaveRulesToService() throws Exception {
        QuizService quizService = mock(QuizService.class);
        SubjectService subjectService = mock(SubjectService.class);
        LevelService levelService = mock(LevelService.class);
        QuizServlet servlet = new QuizServlet(quizService, subjectService, levelService);
        when(subjectService.getAllSubjects()).thenReturn(List.of(new Subject()));
        when(levelService.findAllLevels()).thenReturn(List.of(new Level()));

        WebContext created = quizForm("create", null, " New quiz ");
        when(quizService.saveNewQuiz(any(QuizDto.class))).thenReturn(QuizService.SaveResult.SAVED);
        servlet.doPost(created.request, created.response);
        verify(quizService).saveNewQuiz(any(QuizDto.class));
        verify(created.response).sendRedirect("quizzes");

        WebContext duplicateCreate = quizForm("create", null, "Duplicate");
        when(duplicateCreate.session.getAttribute("lang")).thenReturn(Locale.ENGLISH);
        when(quizService.saveNewQuiz(any(QuizDto.class)))
                .thenReturn(QuizService.SaveResult.DUPLICATE_NAME);
        servlet.doPost(duplicateCreate.request, duplicateCreate.response);
        verify(duplicateCreate.request).setAttribute("quiz", 0);
        verify(duplicateCreate.request).setAttribute("quizName", "Duplicate");
        verify(duplicateCreate.request).setAttribute("quizSubject", "Java");
        verify(duplicateCreate.request).setAttribute("quizComplexity", "easy");
        verify(duplicateCreate.request).setAttribute("quizTime", 10);
        verify(duplicateCreate.request).setAttribute(
                org.mockito.ArgumentMatchers.eq("quizNameMessage"), any());
        verify(duplicateCreate.dispatcher).forward(
                duplicateCreate.request, duplicateCreate.response);

        WebContext updated = quizForm("update", "7", "Renamed");
        when(quizService.saveQuizChanges(any(QuizDto.class))).thenReturn(QuizService.SaveResult.SAVED);
        servlet.doPost(updated.request, updated.response);
        verify(quizService).saveQuizChanges(any(QuizDto.class));
        verify(updated.response).sendRedirect("quizzes");

        WebContext duplicateUpdate = quizForm("update", "7", "Taken");
        when(quizService.saveQuizChanges(any(QuizDto.class)))
                .thenReturn(QuizService.SaveResult.DUPLICATE_NAME);
        servlet.doPost(duplicateUpdate.request, duplicateUpdate.response);
        verify(duplicateUpdate.request).setAttribute("quiz", 7);
        verify(duplicateUpdate.dispatcher).forward(
                duplicateUpdate.request, duplicateUpdate.response);
    }

    @Test
    public void invalidAndMissingResourcesReturnStableErrors() throws Exception {
        QuizService quizService = mock(QuizService.class);
        QuizServlet servlet = new QuizServlet(
                quizService, mock(SubjectService.class), mock(LevelService.class));

        WebContext successfulDelete = context();
        Quiz quiz = quiz(5, "Direct delete");
        when(successfulDelete.request.getParameter("quiz")).thenReturn("5");
        when(quizService.findQuizById(5)).thenReturn(Optional.of(quiz));
        servlet.doDelete(successfulDelete.request, successfulDelete.response);
        verify(quizService).deleteQuiz(quiz);
        verify(successfulDelete.response).sendRedirect("quizzes");

        WebContext invalidDelete = action("delete");
        when(invalidDelete.request.getParameter("quiz")).thenReturn("invalid");
        servlet.doPost(invalidDelete.request, invalidDelete.response);
        verify(invalidDelete.response).sendError(
                HttpServletResponse.SC_BAD_REQUEST, "Parameter must be an integer: quiz");

        WebContext nonPositiveDelete = context();
        when(nonPositiveDelete.request.getParameter("quiz")).thenReturn("0");
        servlet.doDelete(nonPositiveDelete.request, nonPositiveDelete.response);
        verify(nonPositiveDelete.response).sendError(
                HttpServletResponse.SC_BAD_REQUEST, "Parameter must be positive: quiz");

        WebContext missingDelete = action("delete");
        when(missingDelete.request.getParameter("quiz")).thenReturn("99");
        when(quizService.findQuizById(99)).thenReturn(Optional.empty());
        servlet.doPost(missingDelete.request, missingDelete.response);
        verify(missingDelete.response).sendError(
                HttpServletResponse.SC_NOT_FOUND, "Quiz not found: 99");

        WebContext missingName = quizForm("create", null, null);
        servlet.doPost(missingName.request, missingName.response);
        verify(missingName.response).sendError(
                HttpServletResponse.SC_BAD_REQUEST, "Missing or blank parameter: quizName");
    }

    private static Quiz quiz(int id, String name) {
        Quiz quiz = new Quiz();
        quiz.setId(id);
        quiz.setName(name);
        return quiz;
    }

    private static WebContext action(String action) {
        WebContext context = context();
        when(context.request.getParameter("action")).thenReturn(action);
        return context;
    }

    private static WebContext quizForm(String action, String id, String name) {
        WebContext context = action(action);
        when(context.request.getParameter("quiz")).thenReturn(id);
        when(context.request.getParameter("quizName")).thenReturn(name);
        when(context.request.getParameter("subjectName")).thenReturn("Java");
        when(context.request.getParameter("complexity")).thenReturn("easy");
        when(context.request.getParameter("time")).thenReturn("10");
        return context;
    }

    private static WebContext context() {
        return new WebContext();
    }

    private static final class WebContext {
        private final HttpServletRequest request = mock(HttpServletRequest.class);
        private final HttpServletResponse response = mock(HttpServletResponse.class);
        private final HttpSession session = mock(HttpSession.class);
        private final RequestDispatcher dispatcher = mock(RequestDispatcher.class);

        private WebContext() {
            when(request.getSession()).thenReturn(session);
            when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        }
    }
}
