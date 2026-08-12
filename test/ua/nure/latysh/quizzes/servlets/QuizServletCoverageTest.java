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
        when(quizService.findQuizById(4)).thenReturn(quiz);
        when(quizService.findQuizBySubjectName("Java")).thenReturn(List.of(dto));

        WebContext get = context();
        servlet.doGet(get.request, get.response);
        verify(get.request).setAttribute("quizzes", List.of(dto));
        verify(get.dispatcher).forward(get.request, get.response);

        WebContext add = context();
        when(add.request.getParameter("action")).thenReturn("add");
        servlet.doPost(add.request, add.response);
        verify(add.request).setAttribute("complexities", List.of(level));
        verify(add.request).setAttribute("subjects", List.of(subject));
        verify(add.dispatcher).forward(add.request, add.response);

        WebContext delete = context();
        when(delete.request.getParameter("action")).thenReturn("delete");
        when(delete.request.getParameter("quiz")).thenReturn("4");
        servlet.doPost(delete.request, delete.response);
        verify(quizService).deleteQuiz(quiz);
        verify(delete.response).sendRedirect("quizzes");

        WebContext edit = context();
        when(edit.request.getParameter("action")).thenReturn("edit");
        when(edit.request.getParameter("quiz")).thenReturn("4");
        when(edit.request.getParameter("quizName")).thenReturn(" Quiz ");
        when(edit.request.getParameter("quizSubject")).thenReturn("Java");
        when(edit.request.getParameter("quizComplexity")).thenReturn("easy");
        when(edit.request.getParameter("quizTime")).thenReturn("10");
        servlet.doPost(edit.request, edit.response);
        verify(edit.request).setAttribute("quizTime", 10);
        verify(edit.dispatcher).forward(edit.request, edit.response);

        WebContext search = context();
        when(search.request.getParameter("action")).thenReturn("search");
        when(search.request.getParameter("subject")).thenReturn("Java");
        servlet.doPost(search.request, search.response);
        verify(search.request).setAttribute("quizzes", List.of(dto));
        verify(search.request).setAttribute("subjectName", "Java");
        verify(search.dispatcher).forward(search.request, search.response);
    }

    @Test
    public void createCoversNewAndDuplicateQuizNames() throws Exception {
        QuizService quizService = mock(QuizService.class);
        SubjectService subjectService = mock(SubjectService.class);
        LevelService levelService = mock(LevelService.class);
        QuizServlet servlet = new QuizServlet(quizService, subjectService, levelService);
        when(subjectService.getAllSubjects()).thenReturn(List.of(new Subject()));
        when(levelService.findAllLevels()).thenReturn(List.of(new Level()));
        when(quizService.findQuizByName("New quiz")).thenReturn(quiz(1, "Different"));
        when(quizService.findQuizByName("Duplicate")).thenReturn(quiz(2, "Duplicate"));

        WebContext created = quizForm("create", "1", "New quiz");
        servlet.doPost(created.request, created.response);
        verify(quizService).addQuiz(any(QuizDto.class));
        verify(created.response).sendRedirect("quizzes");

        WebContext duplicate = quizForm("create", "2", "Duplicate");
        servlet.doPost(duplicate.request, duplicate.response);
        verify(duplicate.request).setAttribute("quizName", "Duplicate");
        verify(duplicate.request).setAttribute(org.mockito.ArgumentMatchers.eq("quizNameMessage"), any());
        verify(duplicate.dispatcher).forward(duplicate.request, duplicate.response);
    }

    @Test
    public void updateCoversUnchangedDuplicateAndRenamedQuiz() throws Exception {
        QuizService quizService = mock(QuizService.class);
        SubjectService subjectService = mock(SubjectService.class);
        LevelService levelService = mock(LevelService.class);
        QuizServlet servlet = new QuizServlet(quizService, subjectService, levelService);
        when(subjectService.getAllSubjects()).thenReturn(List.of(new Subject()));
        when(levelService.findAllLevels()).thenReturn(List.of(new Level()));
        when(quizService.findQuizByName("Same")).thenReturn(quiz(1, "Same"));
        when(quizService.findQuizByName("Taken")).thenReturn(quiz(9, "Taken"));
        when(quizService.findQuizByName("Renamed")).thenReturn(quiz(5, "Old"));

        WebContext same = quizForm("update", "1", "Same");
        servlet.doPost(same.request, same.response);
        verify(quizService).updateQuiz(any(QuizDto.class));
        verify(same.response).sendRedirect("quizzes");

        WebContext duplicate = quizForm("update", "1", "Taken");
        servlet.doPost(duplicate.request, duplicate.response);
        verify(duplicate.request).setAttribute("quiz", "1");
        verify(duplicate.dispatcher).forward(duplicate.request, duplicate.response);

        WebContext renamed = quizForm("update", "1", "Renamed");
        servlet.doPost(renamed.request, renamed.response);
        verify(renamed.response).sendRedirect("quizzes");
    }

    private static Quiz quiz(int id, String name) {
        Quiz quiz = new Quiz();
        quiz.setId(id);
        quiz.setName(name);
        return quiz;
    }

    private static WebContext quizForm(String action, String id, String name) {
        WebContext context = context();
        when(context.request.getParameter("action")).thenReturn(action);
        when(context.request.getParameter("quiz")).thenReturn(id);
        when(context.request.getParameter("quizName")).thenReturn(name);
        when(context.request.getParameter("subjectName")).thenReturn("Java");
        when(context.request.getParameter("complexity")).thenReturn("easy");
        when(context.request.getParameter("time")).thenReturn("10");
        when(context.session.getAttribute("lang")).thenReturn(Locale.ENGLISH);
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
