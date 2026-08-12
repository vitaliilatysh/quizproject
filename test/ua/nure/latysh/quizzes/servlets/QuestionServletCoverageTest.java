package ua.nure.latysh.quizzes.servlets;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.services.AnswerService;
import ua.nure.latysh.quizzes.services.AttemptService;
import ua.nure.latysh.quizzes.services.QuestionService;
import ua.nure.latysh.quizzes.services.QuizService;
import ua.nure.latysh.quizzes.services.UserService;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class QuestionServletCoverageTest {
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
    public void getAddViewEditAndDeleteActionsAreCovered() throws Exception {
        Dependencies dependencies = new Dependencies();
        QuestionServlet servlet = dependencies.servlet();
        Question question = question(3, 12, "Question");
        List<Answer> answers = answers();
        when(dependencies.questionService.findQuestionsByQuizId(12)).thenReturn(List.of(question));
        when(dependencies.questionService.findQuestionById(3)).thenReturn(question);
        when(dependencies.answerService.findAnswersByQuestionId(3)).thenReturn(answers);

        WebContext get = context();
        servlet.doGet(get.request, get.response);
        verify(get.session).getAttribute("quizTime");
        verify(get.session).getAttribute("quizId");
        verify(get.session).getAttribute("questions");
        verify(get.session).getAttribute("answersPerQuestion");
        verify(get.dispatcher).forward(get.request, get.response);

        WebContext add = action("add");
        when(add.request.getParameter("quiz")).thenReturn("12");
        servlet.doPost(add.request, add.response);
        verify(add.request).setAttribute("quiz", "12");
        verify(add.request).setAttribute("action", "create");
        verify(add.dispatcher).forward(add.request, add.response);

        WebContext view = action("view");
        when(view.request.getParameter("quiz")).thenReturn("12");
        servlet.doPost(view.request, view.response);
        verify(view.request).setAttribute("questions", List.of(question));
        verify(view.dispatcher).forward(view.request, view.response);

        WebContext edit = action("edit");
        when(edit.request.getParameter("question")).thenReturn("3");
        servlet.doPost(edit.request, edit.response);
        verify(edit.request).setAttribute("answerA", "answer-1");
        verify(edit.request).setAttribute("correctAnswerA", false);
        verify(edit.request).setAttribute("quiz", 12);
        verify(edit.dispatcher).forward(edit.request, edit.response);

        WebContext delete = action("delete");
        when(delete.request.getParameter("question")).thenReturn("3");
        servlet.doPost(delete.request, delete.response);
        verify(dependencies.questionService).deleteQuestion(question);
        verify(delete.request).setAttribute("questions", List.of(question));
        verify(delete.dispatcher).forward(delete.request, delete.response);
    }

    @Test
    public void runActionCreatesAttemptAndSessionQuizState() throws Exception {
        Dependencies dependencies = new Dependencies();
        QuestionServlet servlet = dependencies.servlet();
        Quiz quiz = new Quiz();
        quiz.setId(12);
        quiz.setTimeToPass(3);
        User persistedUser = user(8, "alice");
        Question first = question(1, 12, "First");
        Question second = question(2, 12, "Second");
        when(dependencies.quizService.findQuizById(12)).thenReturn(quiz);
        when(dependencies.userService.findUserByLogin("user")).thenReturn(persistedUser);
        when(dependencies.questionService.findQuestionsByQuizId(12)).thenReturn(List.of(first, second));
        when(dependencies.answerService.findAnswersByQuestionId(1)).thenReturn(List.of(answer(1, false)));
        when(dependencies.answerService.findAnswersByQuestionId(2)).thenReturn(List.of());

        WebContext run = action("run");
        when(run.request.getParameter("quiz")).thenReturn("12");
        servlet.doPost(run.request, run.response);

        verify(dependencies.attemptService).saveAttempt(any());
        verify(run.session).setAttribute("quizTime", 180);
        verify(run.session).setAttribute("quizId", 12);
        verify(run.session).setAttribute("questions", List.of(first, second));
        verify(run.session).setAttribute(org.mockito.ArgumentMatchers.eq("answersPerQuestion"), any());
        verify(run.response).sendRedirect("questions");
    }

    @Test
    public void addQuestionCoversValidationAndBothCorrectAnswerPatterns() throws Exception {
        Dependencies dependencies = new Dependencies();
        QuestionServlet servlet = dependencies.servlet();
        Question saved = question(99, 12, "Saved");
        when(dependencies.questionService.addQuestion(anyString(), anyInt())).thenReturn(saved);
        when(dependencies.questionService.findQuestionsByQuizId(12)).thenReturn(List.of(saved));

        WebContext invalid = questionForm("addQuestion");
        servlet.doPost(invalid.request, invalid.response);
        verify(invalid.request).setAttribute(org.mockito.ArgumentMatchers.eq("checkboxAnswersMessage"), any());
        verify(invalid.dispatcher).forward(invalid.request, invalid.response);

        WebContext firstPattern = questionForm("addQuestion");
        stubCorrectAnswers(firstPattern, "A", "not-B", "C", "not-D");
        servlet.doPost(firstPattern.request, firstPattern.response);

        WebContext secondPattern = questionForm("addQuestion");
        stubCorrectAnswers(secondPattern, "not-A", "B", "not-C", "D");
        servlet.doPost(secondPattern.request, secondPattern.response);

        verify(dependencies.questionService, org.mockito.Mockito.times(2)).addQuestion("New question", 12);
        verify(dependencies.answerService, org.mockito.Mockito.times(8)).saveAnswer(any());
        verify(firstPattern.dispatcher, org.mockito.Mockito.times(2)).forward(firstPattern.request, firstPattern.response);
        verify(secondPattern.request).setAttribute("action", "create");
    }

    @Test
    public void editQuestionCoversValidationAndBothCorrectAnswerPatterns() throws Exception {
        Dependencies dependencies = new Dependencies();
        QuestionServlet servlet = dependencies.servlet();
        when(dependencies.questionService.findQuestionById(3))
                .thenAnswer(invocation -> question(3, 12, "Original"));
        when(dependencies.answerService.findAnswersByQuestionId(3))
                .thenAnswer(invocation -> answers());
        when(dependencies.questionService.findQuestionsByQuizId(12))
                .thenReturn(List.of(question(3, 12, "Edited")));

        WebContext invalid = editForm();
        servlet.doPost(invalid.request, invalid.response);
        verify(invalid.request).setAttribute(org.mockito.ArgumentMatchers.eq("checkboxAnswersMessage"), any());
        verify(invalid.dispatcher).forward(invalid.request, invalid.response);

        WebContext firstPattern = editForm();
        stubCorrectAnswers(firstPattern, "A", "not-B", "C", "not-D");
        servlet.doPost(firstPattern.request, firstPattern.response);

        WebContext secondPattern = editForm();
        stubCorrectAnswers(secondPattern, "not-A", "B", "not-C", "D");
        servlet.doPost(secondPattern.request, secondPattern.response);

        verify(dependencies.questionService, org.mockito.Mockito.times(2)).updateQuestion(any());
        verify(dependencies.answerService, org.mockito.Mockito.times(8)).updateAnswer(any());
        verify(firstPattern.request).setAttribute("quiz", 12);
        verify(secondPattern.dispatcher).forward(secondPattern.request, secondPattern.response);
    }

    private static WebContext questionForm(String action) {
        WebContext context = action(action);
        when(context.request.getParameter("quiz")).thenReturn("12");
        when(context.request.getParameter("question")).thenReturn(" New question ");
        when(context.request.getParameter("answerA")).thenReturn(" answer-1 ");
        when(context.request.getParameter("answerB")).thenReturn(" answer-2 ");
        when(context.request.getParameter("answerC")).thenReturn(" answer-3 ");
        when(context.request.getParameter("answerD")).thenReturn(" answer-4 ");
        return context;
    }

    private static WebContext editForm() {
        WebContext context = action("editQuestion");
        when(context.request.getParameter("quiz")).thenReturn("12");
        when(context.request.getParameter("questionId")).thenReturn("3");
        when(context.request.getParameter("question")).thenReturn("Edited");
        when(context.request.getParameter("answerA")).thenReturn("answer-1");
        when(context.request.getParameter("answerB")).thenReturn("answer-2");
        when(context.request.getParameter("answerC")).thenReturn("answer-3");
        when(context.request.getParameter("answerD")).thenReturn("answer-4");
        return context;
    }

    private static void stubCorrectAnswers(WebContext context, String a, String b, String c, String d) {
        when(context.request.getParameter("correctAnswerA")).thenReturn(a);
        when(context.request.getParameter("correctAnswerB")).thenReturn(b);
        when(context.request.getParameter("correctAnswerC")).thenReturn(c);
        when(context.request.getParameter("correctAnswerD")).thenReturn(d);
    }

    private static WebContext action(String action) {
        WebContext context = context();
        when(context.request.getParameter("action")).thenReturn(action);
        return context;
    }

    private static List<Answer> answers() {
        List<Answer> answers = new ArrayList<>();
        answers.add(answer(1, false));
        answers.add(answer(2, true));
        answers.add(answer(3, false));
        answers.add(answer(4, true));
        return answers;
    }

    private static Answer answer(int id, boolean correct) {
        Answer answer = new Answer();
        answer.setId(id);
        answer.setAnswer("answer-" + id);
        answer.setCorrect(correct);
        return answer;
    }

    private static Question question(int id, int quizId, String text) {
        Question question = new Question();
        question.setId(id);
        question.setQuizId(quizId);
        question.setQuestion(text);
        return question;
    }

    private static User user(int id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        return user;
    }

    private static WebContext context() {
        return new WebContext();
    }

    private static final class Dependencies {
        private final QuizService quizService = mock(QuizService.class);
        private final QuestionService questionService = mock(QuestionService.class);
        private final AnswerService answerService = mock(AnswerService.class);
        private final AttemptService attemptService = mock(AttemptService.class);
        private final UserService userService = mock(UserService.class);

        private QuestionServlet servlet() {
            return new QuestionServlet(quizService, questionService, answerService, attemptService, userService);
        }
    }

    private static final class WebContext {
        private final HttpServletRequest request = mock(HttpServletRequest.class);
        private final HttpServletResponse response = mock(HttpServletResponse.class);
        private final HttpSession session = mock(HttpSession.class);
        private final RequestDispatcher dispatcher = mock(RequestDispatcher.class);

        private WebContext() {
            User sessionUser = user(1, "user");
            when(request.getSession()).thenReturn(session);
            when(request.getSession(true)).thenReturn(session);
            when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
            when(session.getAttribute("user")).thenReturn(sessionUser);
            when(session.getAttribute("lang")).thenReturn(Locale.ENGLISH);
        }
    }
}
