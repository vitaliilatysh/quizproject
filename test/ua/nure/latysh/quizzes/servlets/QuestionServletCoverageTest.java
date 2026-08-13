package ua.nure.latysh.quizzes.servlets;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import ua.nure.latysh.quizzes.entities.Answer;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.Question;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.services.AttemptService;
import ua.nure.latysh.quizzes.services.QuestionService;
import ua.nure.latysh.quizzes.services.QuizService;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    public void getAndReadActionsUseValidatedQuestionDetails() throws Exception {
        Dependencies dependencies = new Dependencies();
        QuestionServlet servlet = dependencies.servlet();
        Question question = question(3, 12, "Question");
        List<Answer> answers = answers();
        QuestionService.QuestionDetails details = new QuestionService.QuestionDetails(question, answers);
        when(dependencies.quizService.findQuizById(12)).thenReturn(Optional.of(quiz(12)));
        when(dependencies.questionService.findQuestionsByQuizId(12)).thenReturn(List.of(question));
        when(dependencies.questionService.getQuestionDetails(3)).thenReturn(details);

        WebContext get = context();
        servlet.doGet(get.request, get.response);
        verify(get.dispatcher).forward(get.request, get.response);

        WebContext add = action("add");
        when(add.request.getParameter("quiz")).thenReturn("12");
        servlet.doPost(add.request, add.response);
        verify(add.request).setAttribute("quiz", 12);
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
        verify(edit.request).setAttribute("correctAnswerB", true);
        verify(edit.request).setAttribute("quiz", 12);
        verify(edit.dispatcher).forward(edit.request, edit.response);
    }

    @Test
    public void createAndUpdateDelegateCompleteAggregatesToService() throws Exception {
        Dependencies dependencies = new Dependencies();
        QuestionServlet servlet = dependencies.servlet();
        Question question = question(3, 12, "Original");
        when(dependencies.quizService.findQuizById(12)).thenReturn(Optional.of(quiz(12)));
        when(dependencies.questionService.findQuestionsByQuizId(12)).thenReturn(List.of(question));

        WebContext create = questionForm("addQuestion");
        when(create.request.getParameter("correctAnswerA")).thenReturn("A");
        when(create.request.getParameter("correctAnswerC")).thenReturn("C");
        servlet.doPost(create.request, create.response);
        verify(dependencies.questionService).createQuestion(
                org.mockito.ArgumentMatchers.eq("New question"),
                org.mockito.ArgumentMatchers.eq(12), any());
        verify(create.dispatcher).forward(create.request, create.response);

        WebContext invalid = questionForm("addQuestion");
        servlet.doPost(invalid.request, invalid.response);
        verify(invalid.request).setAttribute(org.mockito.ArgumentMatchers.eq("checkboxAnswersMessage"), any());
        verify(invalid.dispatcher).forward(invalid.request, invalid.response);

        QuestionService.QuestionDetails details = new QuestionService.QuestionDetails(question, answers());
        when(dependencies.questionService.getQuestionDetails(3)).thenReturn(details);
        WebContext update = questionForm("editQuestion");
        when(update.request.getParameter("questionId")).thenReturn("3");
        when(update.request.getParameter("correctAnswerB")).thenReturn("B");
        servlet.doPost(update.request, update.response);
        verify(dependencies.questionService).updateQuestion(
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq("New question"), any());
        verify(update.dispatcher).forward(update.request, update.response);

        WebContext wrongQuiz = questionForm("editQuestion");
        when(wrongQuiz.request.getParameter("questionId")).thenReturn("3");
        when(wrongQuiz.request.getParameter("quiz")).thenReturn("13");
        when(wrongQuiz.request.getParameter("correctAnswerA")).thenReturn("A");
        servlet.doPost(wrongQuiz.request, wrongQuiz.response);
        verify(wrongQuiz.response).sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Question does not belong to quiz: 13");
    }

    @Test
    public void deleteAndRunUseServerOwnedEntities() throws Exception {
        Dependencies dependencies = new Dependencies();
        QuestionServlet servlet = dependencies.servlet();
        Question question = question(3, 12, "Question");
        when(dependencies.questionService.findQuestionById(3)).thenReturn(Optional.of(question));
        when(dependencies.questionService.findQuestionsByQuizId(12)).thenReturn(List.of(question));
        when(dependencies.quizService.findQuizById(12)).thenReturn(Optional.of(quiz(12)));
        when(dependencies.questionService.getQuestionDetails(3))
                .thenReturn(new QuestionService.QuestionDetails(question, answers()));

        WebContext delete = action("delete");
        when(delete.request.getParameter("question")).thenReturn("3");
        servlet.doPost(delete.request, delete.response);
        verify(dependencies.questionService).deleteQuestion(question);
        verify(delete.request).setAttribute("quiz", 12);

        Quiz quiz = quiz(12);
        User user = user();
        Attempt attempt = new Attempt();
        attempt.setId(44);
        attempt.setExpiresAt(new Date(1_700_000_000_000L));
        when(dependencies.quizService.findQuizById(12)).thenReturn(Optional.of(quiz));
        when(dependencies.attemptService.startAttempt(user, quiz)).thenReturn(attempt);
        WebContext run = action("run");
        when(run.request.getParameter("quiz")).thenReturn("12");
        when(run.session.getAttribute("user")).thenReturn(user);
        servlet.doPost(run.request, run.response);
        verify(run.session).setAttribute("quizTime", 180);
        verify(run.session).setAttribute("attemptId", 44);
        verify(run.session).setAttribute("quizExpiresAt", 1_700_000_000_000L);
        verify(run.session).setAttribute(org.mockito.ArgumentMatchers.eq("answersPerQuestion"), any());
        verify(run.response).sendRedirect("questions");
    }

    @Test
    public void malformedAndMissingResourcesReturnClientErrors() throws Exception {
        Dependencies dependencies = new Dependencies();
        QuestionServlet servlet = dependencies.servlet();

        assertBadRequest(servlet, context(), "Missing parameter: action");
        assertBadRequest(servlet, action("unknown"), "Unknown action: unknown");

        WebContext invalidId = action("view");
        when(invalidId.request.getParameter("quiz")).thenReturn("abc");
        assertBadRequest(servlet, invalidId, "Parameter must be an integer: quiz");

        WebContext invalidCheckbox = questionForm("addQuestion");
        when(invalidCheckbox.request.getParameter("correctAnswerA")).thenReturn("yes");
        assertBadRequest(servlet, invalidCheckbox, "Invalid parameter: correctAnswerA");

        WebContext missingQuiz = action("add");
        when(missingQuiz.request.getParameter("quiz")).thenReturn("404");
        when(dependencies.quizService.findQuizById(404)).thenReturn(Optional.empty());
        servlet.doPost(missingQuiz.request, missingQuiz.response);
        verify(missingQuiz.response).sendError(HttpServletResponse.SC_NOT_FOUND, "Quiz not found: 404");

        WebContext missingQuestion = action("delete");
        when(missingQuestion.request.getParameter("question")).thenReturn("404");
        when(dependencies.questionService.findQuestionById(404)).thenReturn(Optional.empty());
        servlet.doPost(missingQuestion.request, missingQuestion.response);
        verify(missingQuestion.response).sendError(HttpServletResponse.SC_NOT_FOUND, "Question not found: 404");

        WebContext missingRunQuiz = action("run");
        when(missingRunQuiz.request.getParameter("quiz")).thenReturn("404");
        servlet.doPost(missingRunQuiz.request, missingRunQuiz.response);
        verify(missingRunQuiz.response).sendError(HttpServletResponse.SC_NOT_FOUND, "Quiz not found: 404");

        WebContext failure = action("view");
        when(failure.request.getParameter("quiz")).thenReturn("12");
        when(dependencies.quizService.findQuizById(12)).thenReturn(Optional.of(quiz(12)));
        when(dependencies.questionService.findQuestionsByQuizId(12))
                .thenThrow(new IllegalStateException("failed"));
        servlet.doPost(failure.request, failure.response);
        verify(failure.response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    private static void assertBadRequest(QuestionServlet servlet, WebContext context, String message)
            throws Exception {
        servlet.doPost(context.request, context.response);
        verify(context.response).sendError(HttpServletResponse.SC_BAD_REQUEST, message);
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

    private static WebContext action(String action) {
        WebContext context = context();
        when(context.request.getParameter("action")).thenReturn(action);
        return context;
    }

    private static List<Answer> answers() {
        List<Answer> answers = new ArrayList<>();
        for (int id = 1; id <= 4; id++) {
            Answer answer = new Answer();
            answer.setId(id);
            answer.setQuestionId(3);
            answer.setAnswer("answer-" + id);
            answer.setCorrect(id == 2);
            answers.add(answer);
        }
        return answers;
    }

    private static Question question(int id, int quizId, String text) {
        Question question = new Question();
        question.setId(id);
        question.setQuizId(quizId);
        question.setQuestion(text);
        return question;
    }

    private static Quiz quiz(int id) {
        Quiz quiz = new Quiz();
        quiz.setId(id);
        quiz.setTimeToPass(3);
        return quiz;
    }

    private static User user() {
        User user = new User();
        user.setId(8);
        user.setLogin("alice");
        return user;
    }

    private static WebContext context() {
        return new WebContext();
    }

    private static final class Dependencies {
        private final QuizService quizService = mock(QuizService.class);
        private final QuestionService questionService = mock(QuestionService.class);
        private final AttemptService attemptService = mock(AttemptService.class);

        private QuestionServlet servlet() {
            return new QuestionServlet(quizService, questionService, attemptService);
        }
    }

    private static final class WebContext {
        private final HttpServletRequest request = mock(HttpServletRequest.class);
        private final HttpServletResponse response = mock(HttpServletResponse.class);
        private final HttpSession session = mock(HttpSession.class);
        private final RequestDispatcher dispatcher = mock(RequestDispatcher.class);

        private WebContext() {
            when(request.getSession()).thenReturn(session);
            when(request.getSession(true)).thenReturn(session);
            when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
            when(session.getAttribute("user")).thenReturn(user());
            when(session.getAttribute("lang")).thenReturn(Locale.ENGLISH);
        }
    }
}

