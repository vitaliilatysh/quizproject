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

@WebServlet("/allresults")
public class ResultAdminServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(ProfileServlet.class);
    private ResultService resultService = new ResultService();
    private UserService userService = new UserService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        String[] answerId = request.getParameterValues("answerId");
        String startRange = request.getParameter("startRange");
        String endRange = request.getParameter("endRange");
        User sessionUser = (User) request.getSession().getAttribute("user");
        User user = userService.findUserByLogin(sessionUser.getLogin());
        List<ResultDto> results = resultService.getAllResultsBetweenFinishDates(startRange, endRange);
        request.setAttribute("userResults", results);
        request.setAttribute("startRange", startRange);
        request.setAttribute("endRange", endRange);
        request.getRequestDispatcher("resultsAdmin.jsp").forward(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession httpSession = req.getSession(true);
        User user = (User) httpSession.getAttribute("user");
        List<ResultDto> results = resultService.getAllResults();
        req.setAttribute("userResults", results);
        req.getRequestDispatcher("resultsAdmin.jsp").forward(req, resp);
        logger.info(user.getLogin() + "opened quiz results");
    }
}
