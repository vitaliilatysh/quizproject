package ua.nure.latysh.quizzes.servlets;

import ua.nure.latysh.quizzes.dto.QuizDto;
import ua.nure.latysh.quizzes.entities.Level;
import ua.nure.latysh.quizzes.entities.Quiz;
import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.services.LevelService;
import ua.nure.latysh.quizzes.services.QuizService;
import ua.nure.latysh.quizzes.services.SubjectService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

@WebServlet("/quizzes")
public class QuizServlet extends HttpServlet {

    private static final String QUIZZES_LOCATION = "quizzes";
    private static final String ADD_VIEW = "/WEB-INF/views/addQuiz.jsp";
    private static final String EDIT_VIEW = "/WEB-INF/views/editQuiz.jsp";
    private static final String QUIZ = "quiz";
    private static final String QUIZ_NAME = "quizName";
    private static final String QUIZ_SUBJECT = "quizSubject";
    private static final String QUIZ_COMPLEXITY = "quizComplexity";
    private static final String QUIZ_TIME = "quizTime";

    private final QuizService quizService;
    private final SubjectService subjectService;
    private final LevelService levelService;

    public QuizServlet() {
        this(new QuizService(), new SubjectService(), new LevelService());
    }

    QuizServlet(QuizService quizService, SubjectService subjectService, LevelService levelService) {
        this.quizService = quizService;
        this.subjectService = subjectService;
        this.levelService = levelService;
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {
        List<QuizDto> quizzes = quizService.getAllQuizzes();
        request.setAttribute("quizzes", quizzes);
        request.getRequestDispatcher("/WEB-INF/views/quizzes.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) {
            doGet(request, response);
            return;
        }

        try {
            switch (action) {
                case "add" -> doPut(request, response);
                case "delete" -> delete(request, response);
                case "edit" -> edit(request, response);
                case "create" -> create(request, response);
                case "update" -> update(request, response);
                case "search" -> search(request, response);
                default -> throw new BadRequestException("Unknown action: " + action);
            }
        } catch (BadRequestException exception) {
            ServletResponseHandler.sendError(
                    response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (ServletException | IOException exception) {
            ServletResponseHandler.internalError(response, exception);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request,
                            HttpServletResponse response) throws ServletException, IOException {
        try {
            delete(request, response);
        } catch (BadRequestException exception) {
            ServletResponseHandler.sendError(
                    response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        }
    }

    private void delete(HttpServletRequest request,
                        HttpServletResponse response) throws BadRequestException {
        int quizId = RequestParameters.positiveInt(request, QUIZ);
        Optional<Quiz> quiz = quizService.findQuizById(quizId);
        if (quiz.isEmpty()) {
            ServletResponseHandler.sendError(
                    response, HttpServletResponse.SC_NOT_FOUND, "Quiz not found: " + quizId);
            return;
        }
        quizService.deleteQuiz(quiz.get());
        ServletResponseHandler.redirect(response, QUIZZES_LOCATION);
    }

    @Override
    protected void doPut(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {
        setFormOptions(request);
        request.getRequestDispatcher(ADD_VIEW).forward(request, response);
    }

    void create(HttpServletRequest request,
                HttpServletResponse response) throws BadRequestException, ServletException, IOException {
        QuizForm form = QuizForm.forCreate(request);
        QuizService.SaveResult result = quizService.saveNewQuiz(form.toDto());
        if (result == QuizService.SaveResult.SAVED) {
            response.sendRedirect(QUIZZES_LOCATION);
            return;
        }
        showDuplicateName(request, response, form, ADD_VIEW);
    }

    void update(HttpServletRequest request,
                HttpServletResponse response) throws BadRequestException, ServletException, IOException {
        QuizForm form = QuizForm.forUpdate(request);
        QuizService.SaveResult result = quizService.saveQuizChanges(form.toDto());
        if (result == QuizService.SaveResult.SAVED) {
            response.sendRedirect(QUIZZES_LOCATION);
            return;
        }
        showDuplicateName(request, response, form, EDIT_VIEW);
    }

    void edit(HttpServletRequest request,
              HttpServletResponse response) throws BadRequestException, ServletException, IOException {
        int quizId = RequestParameters.positiveInt(request, QUIZ);
        String quizName = RequestParameters.requiredText(request, QUIZ_NAME);
        String quizSubject = RequestParameters.requiredText(request, QUIZ_SUBJECT);
        String quizComplexity = RequestParameters.requiredText(request, QUIZ_COMPLEXITY);
        int quizTime = RequestParameters.positiveInt(request, QUIZ_TIME);

        request.setAttribute(QUIZ, quizId);
        request.setAttribute(QUIZ_NAME, quizName);
        request.setAttribute(QUIZ_COMPLEXITY, quizComplexity);
        request.setAttribute(QUIZ_SUBJECT, quizSubject);
        request.setAttribute(QUIZ_TIME, quizTime);
        setFormOptions(request);
        request.getRequestDispatcher(EDIT_VIEW).forward(request, response);
    }

    void search(HttpServletRequest request,
                HttpServletResponse response) throws BadRequestException, ServletException, IOException {
        String subjectName = RequestParameters.requiredText(request, "subject");
        List<QuizDto> foundQuizzes = quizService.findQuizBySubjectName(subjectName);
        request.setAttribute("quizzes", foundQuizzes);
        request.setAttribute("subjectName", subjectName);
        request.getRequestDispatcher("/WEB-INF/views/quizzes.jsp").forward(request, response);
    }

    private void showDuplicateName(HttpServletRequest request,
                                   HttpServletResponse response,
                                   QuizForm form,
                                   String view) throws ServletException, IOException {
        setFormOptions(request);
        request.setAttribute(QUIZ, form.id());
        request.setAttribute(QUIZ_NAME, form.name());
        request.setAttribute(QUIZ_SUBJECT, form.subject());
        request.setAttribute(QUIZ_COMPLEXITY, form.complexity());
        request.setAttribute(QUIZ_TIME, form.time());
        request.setAttribute("quizNameMessage", duplicateNameMessage(request));
        request.getRequestDispatcher(view).forward(request, response);
    }

    private void setFormOptions(HttpServletRequest request) {
        List<Subject> subjects = subjectService.getAllSubjects();
        List<Level> complexities = levelService.findAllLevels();
        request.setAttribute("subjects", subjects);
        request.setAttribute("complexities", complexities);
    }

    private String duplicateNameMessage(HttpServletRequest request) {
        Locale locale = (Locale) request.getSession().getAttribute("lang");
        Locale effectiveLocale = locale == null ? Locale.getDefault() : locale;
        return ResourceBundle.getBundle("messages", effectiveLocale)
                .getString("validation.input.username.exist");
    }

    record QuizForm(int id, String name, String subject, String complexity, int time) {

        static QuizForm forCreate(HttpServletRequest request) throws BadRequestException {
            return from(request, 0);
        }

        static QuizForm forUpdate(HttpServletRequest request) throws BadRequestException {
            return from(request, RequestParameters.positiveInt(request, QUIZ));
        }

        private static QuizForm from(HttpServletRequest request, int id) throws BadRequestException {
            return new QuizForm(
                    id,
                    RequestParameters.requiredText(request, QUIZ_NAME),
                    RequestParameters.requiredText(request, "subjectName"),
                    RequestParameters.requiredText(request, "complexity"),
                    RequestParameters.positiveInt(request, "time"));
        }

        QuizDto toDto() {
            QuizDto quiz = new QuizDto();
            quiz.setId(id);
            quiz.setName(name);
            quiz.setSubjectName(subject);
            quiz.setComplexity(complexity);
            quiz.setTimeToPass(time);
            return quiz;
        }
    }
}
