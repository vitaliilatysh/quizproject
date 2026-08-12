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
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String action = request.getParameter("action");

        if (action == null) {
            doGet(request, response);
            return;
        }
        switch (action) {
            case "add":
                doPut(request, response);
                break;
            case "delete":
                doDelete(request, response);
                break;
            case "edit":
                edit(request, response);
                break;
            case "create":
                create(request, response);
                break;
            case "update":
                update(request, response);
                break;
            case "search":
                search(request, response);
                break;
            default:
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String quizId = req.getParameter("quiz");
        Integer parsedQuizId = parseId(quizId, resp);
        if (parsedQuizId == null) {
            return;
        }
        quizService.findQuizById(parsedQuizId).ifPresent(quizService::deleteQuiz);
        resp.sendRedirect("quizzes");
    }

    private Integer parseId(String value, HttpServletResponse response) throws IOException {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid identifier");
            return null;
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        List<Subject> subjects = subjectService.getAllSubjects();
        List<Level> complexities = levelService.findAllLevels();

        req.setAttribute("complexities", complexities);
        req.setAttribute("subjects", subjects);
        req.getRequestDispatcher("/WEB-INF/views/addQuiz.jsp").forward(req, resp);
    }

    void create(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String quizId = request.getParameter("quiz");
        String quizName = request.getParameter("quizName").trim();
        String quizSubject = request.getParameter("subjectName");
        String quizComplexity = request.getParameter("complexity");
        String quizTime = request.getParameter("time");

        Locale lang = (Locale) request.getSession().getAttribute("lang");
        ResourceBundle mybundle = ResourceBundle.getBundle("messages", lang);

        Optional<Quiz> quiz = quizService.findQuizByName(quizName);

        QuizDto quizDto = new QuizDto();
        quizDto.setTimeToPass(Integer.parseInt(quizTime));
        quizDto.setName(quizName);
        quizDto.setComplexity(quizComplexity);
        quizDto.setSubjectName(quizSubject);

        List<Subject> subjects = subjectService.getAllSubjects();
        List<Level> complexities = levelService.findAllLevels();

        if (!quizName.isEmpty() && quiz.isEmpty()){
            quizService.addQuiz(quizDto);
            response.sendRedirect("quizzes");
        } else if(!quizName.isEmpty()){
            request.setAttribute("complexities", complexities);
            request.setAttribute("quizComplexity", quizComplexity);
            request.setAttribute("quizSubject", quizSubject);
            request.setAttribute("subjects", subjects);
            request.setAttribute("quizTime", quizTime);
            request.setAttribute("quizName", quizName);
            request.setAttribute("quizNameMessage", mybundle.getString("validation.input.username.exist"));
            request.getRequestDispatcher("/WEB-INF/views/addQuiz.jsp").forward(request, response);
        }
    }

    void update(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String quizId = request.getParameter("quiz");
        String quizName = request.getParameter("quizName").trim();
        String quizSubject = request.getParameter("subjectName");
        String quizComplexity = request.getParameter("complexity");
        String quizTime = request.getParameter("time");

        Locale lang = (Locale) request.getSession().getAttribute("lang");
        ResourceBundle mybundle = ResourceBundle.getBundle("messages", lang);

        Optional<Quiz> quiz = quizService.findQuizByName(quizName);

        QuizDto quizDto = new QuizDto();
        quizDto.setId(Integer.parseInt(quizId));
        quizDto.setTimeToPass(Integer.parseInt(quizTime));
        quizDto.setName(quizName);
        quizDto.setComplexity(quizComplexity);
        quizDto.setSubjectName(quizSubject);

        List<Subject> subjects = subjectService.getAllSubjects();
        List<Level> complexities = levelService.findAllLevels();


        if (!quizName.isEmpty() && quiz.isPresent() && quiz.get().getId() == Integer.parseInt(quizId)) {
            quizDto.setName(quizName);
            quizService.updateQuiz(quizDto);
            response.sendRedirect("quizzes");
        } else if (!quizName.isEmpty() && quiz.isPresent()) {
            request.setAttribute("complexities", complexities);
            request.setAttribute("quizComplexity", quizComplexity);
            request.setAttribute("subjects", subjects);
            request.setAttribute("quizTime", quizTime);
            request.setAttribute("quizName", quizName);
            request.setAttribute("quiz", quizId);
            request.setAttribute("quizNameMessage", mybundle.getString("validation.input.username.exist"));
            request.getRequestDispatcher("/WEB-INF/views/editQuiz.jsp").forward(request, response);
        } else if (!quizName.isEmpty()) {
            quizDto.setName(quizName);
            quizService.updateQuiz(quizDto);
            response.sendRedirect("quizzes");
        }
    }

    void edit(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String quizId = request.getParameter("quiz");
        String quizName = request.getParameter("quizName").trim();
        String quizSubject = request.getParameter("quizSubject");
        String quizComplexity = request.getParameter("quizComplexity");
        String quizTime = request.getParameter("quizTime");

        List<Subject> subjects = subjectService.getAllSubjects();
        List<Level> complexities = levelService.findAllLevels();

        request.setAttribute("quiz", quizId);
        request.setAttribute("quizName", quizName);
        request.setAttribute("quizComplexity", quizComplexity);
        request.setAttribute("quizSubject", quizSubject);
        request.setAttribute("quizTime", Integer.parseInt(quizTime));
        request.setAttribute("complexities", complexities);
        request.setAttribute("subjects", subjects);
        request.getRequestDispatcher("/WEB-INF/views/editQuiz.jsp").forward(request, response);
    }

    void search(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String subjectName = request.getParameter("subject");
        if (subjectName != null) {
            List<QuizDto> foundQuizzes = quizService.findQuizBySubjectName(subjectName);

            request.setAttribute("quizzes", foundQuizzes);
            request.setAttribute("subjectName", subjectName);
            request.getRequestDispatcher("/WEB-INF/views/quizzes.jsp").forward(request, response);
        }
    }
}
