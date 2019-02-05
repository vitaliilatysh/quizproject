package ua.nure.latysh.quizzes.servlets;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.services.UserService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.ResourceBundle;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(LoginServlet.class);
    private UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("index.jsp").forward(req, resp);
        logger.info("Log in page opened");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String login = (req.getParameter("username") != null) ? req.getParameter("username").trim() : null;
        String password = (req.getParameter("password") != null) ? req.getParameter("password").trim() : null;
        User user = userService.findByLoginAndPassword(login, password);

        Locale lang = (Locale) req.getSession().getAttribute("lang");
        ResourceBundle mybundle = ResourceBundle.getBundle("messages", lang);

        if (user != null && user.getStatusId() != 2) {
            HttpSession oldSession = req.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession newSession = req.getSession(true);

            newSession.setMaxInactiveInterval(5 * 60);
            newSession.setAttribute("role", user.getRoleId());
            newSession.setAttribute("user", user);
            newSession.setAttribute("lang", lang);

            Cookie userLogin = new Cookie("user", login);
            req.setAttribute("user", user);
            resp.addCookie(userLogin);
            user.setLoginDateTime(new Date());
            userService.updateUserLoginDate(user);
            resp.sendRedirect("quizzes");
            logger.info(user.getLogin() + " logged in");
        } else if (user == null) {

            req.getSession().setAttribute("lang", lang);
            req.setAttribute("loginMessage",mybundle.getString("validation.input.username.notfound"));
            req.setAttribute("username", login);
            req.setAttribute("password", password);
            req.getRequestDispatcher("/").forward(req, resp);
        } else if (user.getStatusId() == 2) {

            req.getSession().setAttribute("lang", lang);
            req.setAttribute("loginMessage", mybundle.getString("validation.user.blocked"));
            req.setAttribute("username", login);
            req.setAttribute("password", password);
            req.getRequestDispatcher("/").forward(req, resp);
        } else if (login.isEmpty() && password.isEmpty()) {
            req.getRequestDispatcher("/").forward(req, resp);
        } else {
            req.getRequestDispatcher("/").include(req, resp);
        }
    }
}
