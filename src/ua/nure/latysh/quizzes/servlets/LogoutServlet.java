package ua.nure.latysh.quizzes.servlets;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.entities.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(LogoutServlet.class);

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        HttpSession session = request.getSession(false);
        User user = (User) request.getSession().getAttribute("user");
        if (session != null) {
            logger.info(user.getLogin() + " logged out");
            session.invalidate();
        }
        response.sendRedirect("/SummaryTask4");
    }
}
