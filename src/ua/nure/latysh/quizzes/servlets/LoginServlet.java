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
    private final UserService userService;

    public LoginServlet() {
        this(new UserService());
    }

    LoginServlet(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("index.jsp").forward(req, resp);
        logger.info("Log in page opened");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String usernameParameter = req.getParameter("username");
        String login = usernameParameter == null ? null : usernameParameter.trim();
        String password = req.getParameter("password");

        Locale lang = (Locale) req.getSession().getAttribute("lang");
        if (lang == null) {
            lang = Locale.getDefault();
        }
        ResourceBundle mybundle = ResourceBundle.getBundle("messages", lang);

        if (login == null || login.isEmpty() || password == null || password.isBlank()) {
            forwardWithError(req, resp, login, mybundle.getString("validation.input.required"));
            return;
        }

        User user = userService.findByLoginAndPassword(login, password);

        if (user == null) {
            forwardWithError(req, resp, login, mybundle.getString("validation.input.username.notfound"));
        } else if (user.getStatusId() == 2) {
            forwardWithError(req, resp, login, mybundle.getString("validation.user.blocked"));
        } else {
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
        }
    }

    private void forwardWithError(HttpServletRequest request, HttpServletResponse response,
                                  String login, String message) throws ServletException, IOException {
        request.setAttribute("loginMessage", message);
        request.setAttribute("username", login);
        request.getRequestDispatcher("/").forward(request, response);
    }
}
