package ua.nure.latysh.quizzes.servlets;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.dto.ResultDto;
import ua.nure.latysh.quizzes.entities.Attempt;
import ua.nure.latysh.quizzes.entities.Result;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.services.AttemptService;
import ua.nure.latysh.quizzes.services.ResultService;
import ua.nure.latysh.quizzes.services.UserService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@WebServlet("/results")
public class ResultServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(ProfileServlet.class);
    private ResultService resultService = new ResultService();
    private UserService userService = new UserService();
    private AttemptService attemptService = new AttemptService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        String[] answerId = request.getParameterValues("answerId");

        User sessionUser = (User) request.getSession().getAttribute("user");
        User user = userService.findUserByLogin(sessionUser.getLogin());
        Attempt attempt = attemptService.findTheLatestForUser(user);

        if(answerId != null) {
            for (String id : answerId) {
                Result result = new Result();
                result.setAnswerId(Integer.parseInt(id));
                result.setAttemptId(attempt.getId());
                resultService.saveResult(result);
            }

        }

        int scoreAttempt = (int) resultService.getResultForQuizByAttemptId(attempt.getId());
        attempt.setScore(scoreAttempt);
        attempt.setEndTime(new Date());
        attemptService.updateAttemptByScore(attempt);

        Cookie cookie = new Cookie("attempt", String.valueOf(attempt.getId()));
        response.addCookie(cookie);
        request.getRequestDispatcher("quizzes").forward(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession httpSession = req.getSession(true);
        User user = (User) httpSession.getAttribute("user");
        List<ResultDto> results = resultService.getAllResultsByUserId(user.getId());
        req.setAttribute("userResults", results);
        req.getRequestDispatcher("results.jsp").forward(req, resp);
        logger.info(user.getLogin() + "opened quiz results");
    }
}
