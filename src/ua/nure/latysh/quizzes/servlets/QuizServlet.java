package ua.nure.latysh.quizzes.servlets;

import org.apache.log4j.Logger;
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
import java.util.ResourceBundle;

@WebServlet("/quizzes")
public class QuizServlet extends HttpServlet {

    private final static Logger logger = Logger.getLogger(QuizServlet.class);
    private QuizService quizService = new QuizService();
    private SubjectService subjectService = new SubjectService();
    private LevelService levelService = new LevelService();

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {
        List<QuizDto> quizzes = quizService.getAllQuizzes();
        request.setAttribute("quizzes", quizzes);
        request.getRequestDispatcher("quizzes.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String action = request.getParameter("action");

        if (action == null) {
            doGet(request, response);

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
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String quizId = req.getParameter("quiz");
        Quiz quiz = quizService.findQuizById(Integer.parseInt(quizId));
        quizService.deleteQuiz(quiz);
        resp.sendRedirect("quizzes");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        List<Subject> subjects = subjectService.getAllSubjects();
        List<Level> complexities = levelService.findAllLevels();

        req.setAttribute("complexities", complexities);
        req.setAttribute("subjects", subjects);
        req.getRequestDispatcher("addQuiz.jsp").forward(req, resp);
    }

    void create(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String quizId = request.getParameter("quiz");
        String quizName = request.getParameter("quizName").trim();
        String quizSubject = request.getParameter("subjectName");
        String quizComplexity = request.getParameter("complexity");
        String quizTime = request.getParameter("time");

        Locale lang = (Locale) request.getSession().getAttribute("lang");
        ResourceBundle mybundle = ResourceBundle.getBundle("messages", lang);

        Quiz quiz = quizService.findQuizByName(quizName);

        QuizDto quizDto = new QuizDto();
        quizDto.setTimeToPass(Integer.parseInt(quizTime));
        quizDto.setName(quizName);
        quizDto.setComplexity(quizComplexity);
        quizDto.setSubjectName(quizSubject);

        List<Subject> subjects = subjectService.getAllSubjects();
        List<Level> complexities = levelService.findAllLevels();

        if (!quizName.isEmpty() && !quizName.equalsIgnoreCase(quiz.getName())){
            quizService.addQuiz(quizDto);
            response.sendRedirect("quizzes");
        } else if(!quizName.isEmpty() && quizName.equalsIgnoreCase(quiz.getName())){
            request.setAttribute("complexities", complexities);
            request.setAttribute("quizComplexity", quizComplexity);
            request.setAttribute("quizSubject", quizSubject);
            request.setAttribute("subjects", subjects);
            request.setAttribute("quizTime", quizTime);
            request.setAttribute("quizName", quizName);
            request.setAttribute("quizNameMessage", mybundle.getString("validation.input.username.exist"));
            request.getRequestDispatcher("addQuiz.jsp").forward(request, response);
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

        Quiz quiz = quizService.findQuizByName(quizName);

        QuizDto quizDto = new QuizDto();
        quizDto.setId(Integer.parseInt(quizId));
        quizDto.setTimeToPass(Integer.parseInt(quizTime));
        quizDto.setName(quizName);
        quizDto.setComplexity(quizComplexity);
        quizDto.setSubjectName(quizSubject);

        List<Subject> subjects = subjectService.getAllSubjects();
        List<Level> complexities = levelService.findAllLevels();


        if (!quizName.isEmpty() && quizName.equalsIgnoreCase(quiz.getName()) && quiz.getId() == Integer.parseInt(quizId)) {
            quizDto.setName(quizName);
            quizService.updateQuiz(quizDto);
            response.sendRedirect("quizzes");
        } else if (!quizName.isEmpty() && quizName.equalsIgnoreCase(quiz.getName()) && quiz.getId() != Integer.parseInt(quizId)) {
            request.setAttribute("complexities", complexities);
            request.setAttribute("quizComplexity", quizComplexity);
            request.setAttribute("subjects", subjects);
            request.setAttribute("quizTime", quizTime);
            request.setAttribute("quizName", quizName);
            request.setAttribute("quiz", quizId);
            request.setAttribute("quizNameMessage", mybundle.getString("validation.input.username.exist"));
            request.getRequestDispatcher("editQuiz.jsp").forward(request, response);
        } else if (!quizName.isEmpty() && !quizName.equalsIgnoreCase(quiz.getName())) {
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
        request.getRequestDispatcher("editQuiz.jsp").forward(request, response);
    }

    void search(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String subjectName = request.getParameter("subject");
        if (subjectName != null) {
            List<QuizDto> foundQuizzes = quizService.findQuizBySubjectName(subjectName);

            request.setAttribute("quizzes", foundQuizzes);
            request.setAttribute("subjectName", subjectName);
            request.getRequestDispatcher("quizzes.jsp").forward(request, response);
        }
    }
}
