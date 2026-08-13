package ua.nure.latysh.quizzes.servlets;

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
    private static final String RESULTS_VIEW = "/WEB-INF/views/results.jsp";

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
            ServletResponseHandler.sendError(response, HttpServletResponse.SC_CONFLICT, "No active attempt");
            return;
        }

        Set<Integer> answerIds;
        try {
            answerIds = parseAnswerIds(request.getParameterValues("answerId"));
        } catch (BadRequestException exception) {
            ServletResponseHandler.sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid answer id");
            return;
        }

        try {
            attemptService.completeAttempt((Integer) attemptAttribute, user.getId(), answerIds);
        } catch (QuizSubmissionException exception) {
            int status = exception.getReason() == QuizSubmissionException.Reason.INVALID_ANSWER
                    ? HttpServletResponse.SC_BAD_REQUEST : HttpServletResponse.SC_CONFLICT;
            ServletResponseHandler.sendError(response, status, exception.getMessage());
            return;
        }

        clearAttempt(session);
        ServletResponseHandler.redirect(response, "quizzes");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("user") instanceof User user)) {
            ServletResponseHandler.sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }
        List<ResultDto> results = resultService.getAllResultsByUserId(user.getId());
        request.setAttribute("userResults", results);
        ServletResponseHandler.forward(request.getRequestDispatcher(RESULTS_VIEW), request, response);
    }

    private Set<Integer> parseAnswerIds(String[] values) throws BadRequestException {
        Set<Integer> answerIds = new HashSet<>();
        if (values != null) {
            for (String value : values) {
                try {
                    int answerId = Integer.parseInt(value);
                    if (answerId <= 0) {
                        throw new BadRequestException("Invalid answer id");
                    }
                    answerIds.add(answerId);
                } catch (NumberFormatException exception) {
                    throw new BadRequestException("Invalid answer id");
                }
            }
        }
        return answerIds;
    }

    private void clearAttempt(HttpSession session) {
        session.removeAttribute("attemptId");
        session.removeAttribute("quizId");
        session.removeAttribute("quizTime");
        session.removeAttribute("quizExpiresAt");
    }
}
