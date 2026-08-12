package ua.nure.latysh.quizzes.servlets;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.dto.ResultDto;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.exceptions.QuizSubmissionException;
import ua.nure.latysh.quizzes.services.AttemptService;
import ua.nure.latysh.quizzes.services.ResultService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@WebServlet("/results")
public class ResultServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(ResultServlet.class);

    private final ResultService resultService;
    private final AttemptService attemptService;

    public ResultServlet() {
        this(new ResultService(), new AttemptService());
    }

    ResultServlet(ResultService resultService, AttemptService attemptService) {
        this.resultService = resultService;
        this.attemptService = attemptService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        Object attemptAttribute = session == null ? null : session.getAttribute("attemptId");
        User user = session == null ? null : (User) session.getAttribute("user");
        if (!(attemptAttribute instanceof Integer) || user == null) {
            response.sendError(HttpServletResponse.SC_CONFLICT, "No active attempt");
            return;
        }

        Set<Integer> answerIds;
        try {
            answerIds = parseAnswerIds(request.getParameterValues("answerId"));
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid answer id");
            return;
        }

        try {
            attemptService.completeAttempt((Integer) attemptAttribute, user.getId(), answerIds);
        } catch (QuizSubmissionException exception) {
            int status = exception.getReason() == QuizSubmissionException.Reason.INVALID_ANSWER
                    ? HttpServletResponse.SC_BAD_REQUEST : HttpServletResponse.SC_CONFLICT;
            response.sendError(status, exception.getMessage());
            return;
        }

        clearAttempt(session);
        response.sendRedirect("quizzes");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");
        List<ResultDto> results = resultService.getAllResultsByUserId(user.getId());
        request.setAttribute("userResults", results);
        request.getRequestDispatcher("/WEB-INF/views/results.jsp").forward(request, response);
        logger.info(user.getLogin() + " opened quiz results");
    }

    private Set<Integer> parseAnswerIds(String[] values) {
        Set<Integer> answerIds = new HashSet<>();
        if (values != null) {
            for (String value : values) {
                answerIds.add(Integer.parseInt(value));
            }
        }
        return answerIds;
    }

    private void clearAttempt(HttpSession session) {
        session.removeAttribute("attemptId");
        session.removeAttribute("quizId");
        session.removeAttribute("quizTime");
        session.removeAttribute("quizExpiresAt");
        session.removeAttribute("questions");
        session.removeAttribute("answersPerQuestion");
    }
}
