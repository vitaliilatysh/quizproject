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

@WebServlet("/signup")
public class SignupServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(SignupServlet.class);
    private UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher("signup.jsp").forward(req, resp);
        logger.info("Signup page was opened");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String login = req.getParameter("username");
        String firstName = req.getParameter("firstName");
        String lastName = req.getParameter("lastName");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");

        User newUser = new User();
        newUser.setLogin(login);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setPassword(password);
        newUser.setRegisterDateTime(new Date());
        newUser.setRoleId(2);
        newUser.setStatusId(1);

        User user = userService.findUserByLogin(login);
        Locale lang = (Locale) req.getSession().getAttribute("lang");
        ResourceBundle mybundle = ResourceBundle.getBundle("messages", lang);
        req.getSession().setAttribute("lang", lang);

        if (user == null && password != null && password.equalsIgnoreCase(confirmPassword)) {
            userService.save(newUser);
            User savedUser = userService.findUserByLogin(newUser.getLogin());
            HttpSession oldSession = req.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession newSession = req.getSession(true);
            newSession.setMaxInactiveInterval(5 * 60);
            newSession.setAttribute("role", savedUser.getRoleId());
            newSession.setAttribute("user", savedUser);
            newSession.setAttribute("lang", lang);

            Cookie userLogin = new Cookie("user", savedUser.getLogin());
            resp.addCookie(userLogin);
            savedUser.setLoginDateTime(new Date());
            userService.updateUserLoginDate(savedUser);

            req.setAttribute("user", savedUser);
            resp.sendRedirect("quizzes");
            logger.info(newUser.getLogin() + "logged in");
        } else if(user != null && !password.equalsIgnoreCase(confirmPassword)){

            req.setAttribute("usernameMessage", mybundle.getString("validation.input.username.exist"));
            req.setAttribute("confirmPwMessage", mybundle.getString("validation.password"));
            req.setAttribute("username", login);
            req.setAttribute("firstName", firstName);
            req.setAttribute("lastName", lastName);
            req.setAttribute("password", password);
            req.setAttribute("confirmPassword", confirmPassword);
            req.getRequestDispatcher("signup.jsp").forward(req, resp);
            logger.info(user.getLogin() + "has mismatching passwords");
        } else if(user != null && password.equalsIgnoreCase(confirmPassword)){
            req.setAttribute("username", login);
            req.setAttribute("firstName", firstName);
            req.setAttribute("lastName", lastName);
            req.setAttribute("password", password);
            req.setAttribute("confirmPassword", confirmPassword);
            req.setAttribute("usernameMessage", mybundle.getString("validation.input.username.exist"));
            req.getRequestDispatcher("signup.jsp").forward(req, resp);
            logger.info(user.getLogin() + "already exists");
        } else if(user == null && password !=null && !password.equalsIgnoreCase(confirmPassword)){
            req.setAttribute("confirmPwMessage", mybundle.getString("validation.password"));
            req.setAttribute("username", login);
            req.setAttribute("firstName", firstName);
            req.setAttribute("lastName", lastName);
            req.setAttribute("password", password);
            req.setAttribute("confirmPassword", confirmPassword);
            req.getRequestDispatcher("signup.jsp").forward(req, resp);
            logger.info("Passwords mismatched");
        }else{
            resp.sendRedirect("signup");

        }
    }
}
