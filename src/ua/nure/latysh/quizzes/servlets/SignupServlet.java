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
import java.util.Optional;
import java.util.ResourceBundle;

@WebServlet("/signup")
public class SignupServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(SignupServlet.class);
    private final UserService userService;

    public SignupServlet() {
        this(new UserService());
    }

    SignupServlet(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ServletResponseHandler.forward(req.getRequestDispatcher("/WEB-INF/views/signup.jsp"), req, resp);
        logger.info("Signup page was opened");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String usernameParameter = req.getParameter("username");
        String login = usernameParameter == null ? null : usernameParameter.trim();
        String firstName = req.getParameter("firstName");
        String lastName = req.getParameter("lastName");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");

        Locale lang = (Locale) req.getSession().getAttribute("lang");
        if (lang == null) {
            lang = Locale.getDefault();
        }
        ResourceBundle mybundle = ResourceBundle.getBundle("messages", lang);

        if (login == null || login.isEmpty() || password == null || password.isBlank()
                || confirmPassword == null || confirmPassword.isBlank()
                || password.length() < 8 || password.length() > 128
                || password.chars().anyMatch(Character::isWhitespace)) {
            populateSignupForm(req, login, firstName, lastName);
            req.setAttribute("confirmPwMessage", mybundle.getString("validation.input.required"));
            ServletResponseHandler.forward(req.getRequestDispatcher("/WEB-INF/views/signup.jsp"), req, resp);
            return;
        }

        User newUser = new User();
        newUser.setLogin(login);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setPassword(password);
        newUser.setRegisterDateTime(new Date());
        newUser.setRoleId(2);
        newUser.setStatusId(1);

        Optional<User> user = userService.findUserByLogin(login);
        boolean passwordsMatch = password.equals(confirmPassword);

        if (user.isEmpty() && passwordsMatch) {
            userService.save(newUser);
            Optional<User> reloadedUser = userService.findUserByLogin(newUser.getLogin());
            if (reloadedUser.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            User savedUser = reloadedUser.get();
            savedUser.setPassword(null);
            HttpSession oldSession = req.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession newSession = req.getSession(true);
            newSession.setMaxInactiveInterval(5 * 60);
            newSession.setAttribute("role", savedUser.getRoleId());
            newSession.setAttribute("user", savedUser);
            newSession.setAttribute("lang", lang);

            savedUser.setLoginDateTime(new Date());
            userService.updateUserLoginDate(savedUser);

            req.setAttribute("user", savedUser);
            ServletResponseHandler.redirect(resp, "quizzes");
            logger.info(newUser.getLogin() + "logged in");
        } else {
            populateSignupForm(req, login, firstName, lastName);
            if (user.isPresent()) {
                req.setAttribute("usernameMessage", mybundle.getString("validation.input.username.exist"));
            }
            if (!passwordsMatch) {
                req.setAttribute("confirmPwMessage", mybundle.getString("validation.password"));
            }
            ServletResponseHandler.forward(req.getRequestDispatcher("/WEB-INF/views/signup.jsp"), req, resp);
        }
    }

    private void populateSignupForm(HttpServletRequest request, String login,
                                    String firstName, String lastName) {
        request.setAttribute("username", login);
        request.setAttribute("firstName", firstName);
        request.setAttribute("lastName", lastName);
    }
}
