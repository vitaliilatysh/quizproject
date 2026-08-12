package ua.nure.latysh.quizzes.servlets;

import org.apache.log4j.Logger;
import ua.nure.latysh.quizzes.entities.User;
import ua.nure.latysh.quizzes.services.UserService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet("/profile")
public class ProfileServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(ProfileServlet.class);
    private final UserService userService;

    public ProfileServlet() {
        this(new UserService());
    }

    ProfileServlet(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession httpSession = req.getSession(true);
        User user = (User) httpSession.getAttribute("user");

        req.setAttribute("user", userService.convertUserToUserDto(user));
        req.getRequestDispatcher("/WEB-INF/views/profile.jsp").forward(req, resp);
        logger.info(user.getLogin() + "opened the profile");
    }
}
