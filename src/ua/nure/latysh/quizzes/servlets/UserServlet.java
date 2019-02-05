package ua.nure.latysh.quizzes.servlets;

import ua.nure.latysh.quizzes.dto.UserDto;
import ua.nure.latysh.quizzes.services.UserService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet("/users")
public class UserServlet extends HttpServlet {
    private UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        List<UserDto> userList = userService.findAllUsers();

        req.setAttribute("users", userList);
        req.getRequestDispatcher("users.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        String userId = req.getParameter("userId");

        if(action != null && action.equalsIgnoreCase("block")){
            userService.blockUser(userId);
        } else if(action != null && action.equalsIgnoreCase("activate")){
            userService.unblockUser(userId);
        }

        resp.sendRedirect("users");
    }
}
