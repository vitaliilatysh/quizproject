package ua.nure.latysh.quizzes.servlets;

import org.apache.log4j.Logger;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

final class ServletResponseHandler {
    private static final Logger logger = Logger.getLogger(ServletResponseHandler.class);

    ServletResponseHandler() {
    }

    static void forward(RequestDispatcher dispatcher, HttpServletRequest request,
                        HttpServletResponse response) {
        try {
            dispatcher.forward(request, response);
        } catch (ServletException | IOException exception) {
            failSafely(response, exception);
        }
    }

    static void sendError(HttpServletResponse response, int status, String message) {
        try {
            response.sendError(status, message);
        } catch (IOException exception) {
            failSafely(response, exception);
        }
    }

    static void redirect(HttpServletResponse response, String location) {
        try {
            response.sendRedirect(location);
        } catch (IOException exception) {
            failSafely(response, exception);
        }
    }

    private static void failSafely(HttpServletResponse response, Exception exception) {
        logger.error("Could not write the servlet response", exception);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
}
