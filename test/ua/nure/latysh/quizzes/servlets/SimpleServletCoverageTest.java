package ua.nure.latysh.quizzes.servlets;

import org.junit.Test;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import ua.nure.latysh.quizzes.dto.ResultDto;
import ua.nure.latysh.quizzes.dto.UserDto;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.services.AttemptService;
import ua.nure.latysh.quizzes.services.ResultService;
import ua.nure.latysh.quizzes.services.SubjectService;
import ua.nure.latysh.quizzes.services.UserService;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SimpleServletCoverageTest {
    private static Locale originalLocale;

    @BeforeClass
    public static void useEnglishFallbackBundle() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterClass
    public static void restoreDefaultLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    public void defaultConstructorsAndLanguageEndpointAreUsable() throws Exception {
        assertNotNull(new LangServlet());
        assertNotNull(new LoginServlet());
        assertNotNull(new LogoutServlet());
        assertNotNull(new ProfileServlet());
        assertNotNull(new QuestionServlet());
        assertNotNull(new QuizServlet());
        assertNotNull(new ResultAdminServlet());
        assertNotNull(new ResultServlet());
        assertNotNull(new SignupServlet());
        assertNotNull(new SubjectServlet());
        assertNotNull(new UserServlet());
        new LangServlet().doGet(mock(HttpServletRequest.class), mock(HttpServletResponse.class));
    }

    @Test
    public void loginServletCoversPageUnknownBlockedAndSuccessfulLogin() throws Exception {
        UserService userService = mock(UserService.class);
        LoginServlet servlet = new LoginServlet(userService);

        WebContext page = context();
        servlet.doGet(page.request, page.response);
        verify(page.dispatcher).forward(page.request, page.response);

        when(userService.findByLoginAndPassword("missing", "secret")).thenReturn(null);
        WebContext missing = context();
        stubLogin(missing, " missing ", "secret", Locale.ENGLISH);
        servlet.doPost(missing.request, missing.response);
        verify(missing.request).setAttribute(org.mockito.ArgumentMatchers.eq("loginMessage"), any());
        verify(missing.dispatcher).forward(missing.request, missing.response);

        User blockedUser = user(2, "blocked", 2);
        when(userService.findByLoginAndPassword("blocked", "secret")).thenReturn(blockedUser);
        WebContext blocked = context();
        stubLogin(blocked, "blocked", "secret", Locale.ENGLISH);
        servlet.doPost(blocked.request, blocked.response);
        verify(blocked.dispatcher).forward(blocked.request, blocked.response);

        User activeUser = user(3, "active", 1);
        when(userService.findByLoginAndPassword("active", "secret")).thenReturn(activeUser);
        WebContext success = context();
        HttpSession oldSession = mock(HttpSession.class);
        HttpSession newSession = mock(HttpSession.class);
        stubLogin(success, "active", "secret", null);
        when(success.request.getSession(false)).thenReturn(oldSession);
        when(success.request.getSession(true)).thenReturn(newSession);
        servlet.doPost(success.request, success.response);
        verify(oldSession).invalidate();
        verify(newSession).setMaxInactiveInterval(300);
        verify(userService).updateUserLoginDate(activeUser);
        verify(success.response).sendRedirect("quizzes");
    }

    @Test
    public void signupServletCoversPageRequiredExistingAndSuccessfulRegistration() throws Exception {
        UserService userService = mock(UserService.class);
        SignupServlet servlet = new SignupServlet(userService);

        WebContext page = context();
        servlet.doGet(page.request, page.response);
        verify(page.dispatcher).forward(page.request, page.response);

        WebContext required = context();
        stubSignup(required, null, "First", "Last", null, null, null);
        servlet.doPost(required.request, required.response);
        verify(required.dispatcher).forward(required.request, required.response);

        User existingUser = user(4, "existing", 1);
        when(userService.findUserByLogin("existing")).thenReturn(existingUser);
        WebContext existing = context();
        stubSignup(existing, "existing", "First", "Last", "password-one", "password-two", Locale.ENGLISH);
        servlet.doPost(existing.request, existing.response);
        verify(existing.request).setAttribute("usernameMessage",
                java.util.ResourceBundle.getBundle("messages", Locale.ENGLISH)
                        .getString("validation.input.username.exist"));
        verify(existing.request).setAttribute("confirmPwMessage",
                java.util.ResourceBundle.getBundle("messages", Locale.ENGLISH)
                        .getString("validation.password"));

        User savedUser = user(5, "new-user", 1);
        when(userService.findUserByLogin("new-user")).thenReturn(null, savedUser);
        WebContext success = context();
        HttpSession oldSession = mock(HttpSession.class);
        HttpSession newSession = mock(HttpSession.class);
        stubSignup(success, " new-user ", "First", "Last", "strong-password", "strong-password", null);
        when(success.request.getSession(false)).thenReturn(oldSession);
        when(success.request.getSession(true)).thenReturn(newSession);
        servlet.doPost(success.request, success.response);
        verify(userService).save(any(User.class));
        verify(oldSession).invalidate();
        verify(newSession).setAttribute("user", savedUser);
        verify(userService).updateUserLoginDate(savedUser);
        verify(success.response).sendRedirect("quizzes");
    }

    @Test
    public void logoutAndProfileServletsCoverAuthenticatedPaths() throws Exception {
        User user = user(7, "alice", 1);
        WebContext logout = context();
        when(logout.request.getSession(false)).thenReturn(logout.session);
        when(logout.session.getAttribute("user")).thenReturn(user);
        when(logout.request.getContextPath()).thenReturn("/quiz");
        new LogoutServlet().doPost(logout.request, logout.response);
        verify(logout.session).invalidate();
        verify(logout.response).sendRedirect("/quiz/");

        UserService userService = mock(UserService.class);
        UserDto dto = new UserDto();
        when(userService.convertUserToUserDto(user)).thenReturn(dto);
        ProfileServlet profileServlet = new ProfileServlet(userService);
        WebContext profile = context();
        when(profile.request.getSession(true)).thenReturn(profile.session);
        when(profile.session.getAttribute("user")).thenReturn(user);
        profileServlet.doGet(profile.request, profile.response);
        verify(profile.request).setAttribute("user", dto);
        verify(profile.dispatcher).forward(profile.request, profile.response);
    }

    @Test
    public void resultServletCoversSubmissionAndUserHistory() throws Exception {
        ResultService resultService = mock(ResultService.class);
        AttemptService attemptService = mock(AttemptService.class);
        ResultServlet servlet = new ResultServlet(resultService, attemptService);
        User sessionUser = user(8, "alice", 1);

        WebContext submit = context();
        when(submit.request.getParameterValues("answerId")).thenReturn(new String[]{"11", "12"});
        when(submit.request.getSession(false)).thenReturn(submit.session);
        when(submit.session.getAttribute("user")).thenReturn(sessionUser);
        when(submit.session.getAttribute("attemptId")).thenReturn(9);
        servlet.doPost(submit.request, submit.response);
        verify(attemptService).completeAttempt(9, 8, java.util.Set.of(11, 12));
        verify(submit.session).removeAttribute("attemptId");
        verify(submit.response).sendRedirect("quizzes");

        ResultDto resultDto = new ResultDto();
        when(resultService.getAllResultsByUserId(8)).thenReturn(List.of(resultDto));
        WebContext history = context();
        when(history.request.getSession(false)).thenReturn(history.session);
        when(history.session.getAttribute("user")).thenReturn(sessionUser);
        servlet.doGet(history.request, history.response);
        verify(history.request).setAttribute("userResults", List.of(resultDto));
        verify(history.dispatcher).forward(history.request, history.response);
    }

    @Test
    public void resultAdminServletCoversDateFilterAndListing() throws Exception {
        ResultService resultService = mock(ResultService.class);
        UserService userService = mock(UserService.class);
        ResultAdminServlet servlet = new ResultAdminServlet(resultService, userService);
        User user = user(10, "admin", 1);
        ResultDto dto = new ResultDto();
        when(userService.findUserByLogin("admin")).thenReturn(user);
        when(resultService.getAllResultsBetweenFinishDates("from", "to")).thenReturn(List.of(dto));

        WebContext filtered = context();
        when(filtered.request.getParameter("startRange")).thenReturn("from");
        when(filtered.request.getParameter("endRange")).thenReturn("to");
        when(filtered.session.getAttribute("user")).thenReturn(user);
        servlet.doPost(filtered.request, filtered.response);
        verify(filtered.request).setAttribute("userResults", List.of(dto));
        verify(filtered.dispatcher).forward(filtered.request, filtered.response);

        when(resultService.getAllResults()).thenReturn(List.of(dto));
        WebContext all = context();
        when(all.request.getSession(true)).thenReturn(all.session);
        when(all.session.getAttribute("user")).thenReturn(user);
        servlet.doGet(all.request, all.response);
        verify(all.request).setAttribute("userResults", List.of(dto));
        verify(all.dispatcher).forward(all.request, all.response);
    }

    @Test
    public void subjectServletCoversListCreateDeleteEditAndUpdate() throws Exception {
        SubjectService service = mock(SubjectService.class);
        SubjectServlet servlet = new SubjectServlet(service);
        Subject subject = new Subject();
        subject.setId(2);
        subject.setName("Java");
        when(service.getAllSubjects()).thenReturn(List.of(subject));
        when(service.findSubjectById(2)).thenReturn(subject);

        WebContext list = context();
        servlet.doGet(list.request, list.response);
        verify(list.request).setAttribute("subjects", List.of(subject));
        verify(list.dispatcher).forward(list.request, list.response);

        WebContext create = context();
        when(create.request.getParameter("subjectNewName")).thenReturn("Databases");
        servlet.doPost(create.request, create.response);
        verify(service).addSubject(any(Subject.class));
        verify(create.response).sendRedirect("subjects");

        WebContext delete = context();
        when(delete.request.getParameter("delete")).thenReturn("yes");
        when(delete.request.getParameter("subjectId")).thenReturn("2");
        servlet.doPost(delete.request, delete.response);
        verify(service).deleteSubject(subject);

        WebContext edit = context();
        when(edit.request.getParameter("edit")).thenReturn("yes");
        when(edit.request.getParameter("subjectId")).thenReturn("2");
        servlet.doPost(edit.request, edit.response);
        verify(edit.request).setAttribute("subject", subject);
        verify(edit.dispatcher).forward(edit.request, edit.response);

        WebContext update = context();
        when(update.request.getParameter("subjectId")).thenReturn("2");
        when(update.request.getParameter("subjectUpdatedName")).thenReturn("Updated");
        servlet.doPost(update.request, update.response);
        verify(service).updateSubject(subject);
        verify(update.response).sendRedirect("subjects");
    }

    @Test
    public void userServletCoversListingBlockAndActivation() throws Exception {
        UserService service = mock(UserService.class);
        UserServlet servlet = new UserServlet(service);
        UserDto dto = new UserDto();
        when(service.findAllUsers()).thenReturn(List.of(dto));
        WebContext list = context();
        servlet.doGet(list.request, list.response);
        verify(list.request).setAttribute("users", List.of(dto));
        verify(list.dispatcher).forward(list.request, list.response);

        WebContext block = context();
        when(block.request.getParameter("action")).thenReturn("block");
        when(block.request.getParameter("userId")).thenReturn("4");
        servlet.doPost(block.request, block.response);
        verify(service).blockUser("4");
        verify(block.response).sendRedirect("users");

        WebContext activate = context();
        when(activate.request.getParameter("action")).thenReturn("activate");
        when(activate.request.getParameter("userId")).thenReturn("4");
        servlet.doPost(activate.request, activate.response);
        verify(service).unblockUser("4");
        verify(activate.response).sendRedirect("users");
    }

    private static void stubLogin(WebContext context, String username, String password, Locale locale) {
        when(context.request.getParameter("username")).thenReturn(username);
        when(context.request.getParameter("password")).thenReturn(password);
        when(context.session.getAttribute("lang")).thenReturn(locale);
    }

    private static void stubSignup(WebContext context, String username, String firstName, String lastName,
                                   String password, String confirmation, Locale locale) {
        when(context.request.getParameter("username")).thenReturn(username);
        when(context.request.getParameter("firstName")).thenReturn(firstName);
        when(context.request.getParameter("lastName")).thenReturn(lastName);
        when(context.request.getParameter("password")).thenReturn(password);
        when(context.request.getParameter("confirmPassword")).thenReturn(confirmation);
        when(context.session.getAttribute("lang")).thenReturn(locale);
    }

    private static User user(int id, String login, int statusId) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        user.setStatusId(statusId);
        user.setRoleId(2);
        return user;
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
