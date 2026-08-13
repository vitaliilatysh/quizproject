package ua.nure.latysh.quizzes.servlets;

import ua.nure.latysh.quizzes.dto.ResultDto;
import ua.nure.latysh.quizzes.services.ResultService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.List;

@WebServlet("/allresults")
public class ResultAdminServlet extends HttpServlet {
    private static final String RESULTS_VIEW = "/WEB-INF/views/resultsAdmin.jsp";

    private final ResultService resultService;

    public ResultAdminServlet() {
        this(new ResultService());
    }

    ResultAdminServlet(ResultService resultService) {
        this.resultService = resultService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            LocalDateTime startRange = RequestParameters.localDateTime(request, "startRange");
            LocalDateTime endRange = RequestParameters.localDateTime(request, "endRange");
            List<ResultDto> results = resultService.getAllResultsBetweenFinishDates(startRange, endRange);
            request.setAttribute("userResults", results);
            request.setAttribute("startRange", startRange.toString());
            request.setAttribute("endRange", endRange.toString());
            showResults(request, response);
        } catch (BadRequestException | IllegalArgumentException exception) {
            ServletResponseHandler.sendError(response, HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
        } catch (RuntimeException exception) {
            ServletResponseHandler.internalError(response, exception);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            request.setAttribute("userResults", resultService.getAllResults());
            showResults(request, response);
        } catch (RuntimeException exception) {
            ServletResponseHandler.internalError(response, exception);
        }
    }

    private void showResults(HttpServletRequest request, HttpServletResponse response) {
        ServletResponseHandler.forward(request.getRequestDispatcher(RESULTS_VIEW), request, response);
    }
}
