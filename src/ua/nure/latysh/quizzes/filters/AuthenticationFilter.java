package ua.nure.latysh.quizzes.filters;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebFilter(filterName = "AuthenticationFilter", urlPatterns = {"/*"})
public class AuthenticationFilter implements Filter {
    static final int ADMIN_ROLE = 1;
    static final int STUDENT_ROLE = 2;

    private ServletContext context;

    @Override
    public void init(FilterConfig filterConfig) {
        context = filterConfig.getServletContext();
        context.log("AuthenticationFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getServletPath();

        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = httpRequest.getSession(false);
        if (session == null || !(session.getAttribute("role") instanceof Integer)) {
            context.log("Unauthorized access request");
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/");
            return;
        }

        int role = (Integer) session.getAttribute("role");
        if (!isAuthorized(httpRequest, role)) {
            context.log("Forbidden access request to " + path);
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        httpResponse.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        httpResponse.setHeader("Pragma", "no-cache");
        httpResponse.setDateHeader("Expires", 0);
        httpRequest.setCharacterEncoding("UTF-8");
        chain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        return path == null || path.isEmpty() || "/".equals(path)
                || "/login".equals(path) || "/signup".equals(path) || "/lang".equals(path)
                || path.startsWith("/static/") || "/favicon.ico".equals(path);
    }

    private boolean isAuthorized(HttpServletRequest request, int role) {
        String path = request.getServletPath();
        if ("/subjects".equals(path) || "/users".equals(path) || "/allresults".equals(path)) {
            return role == ADMIN_ROLE;
        }
        if ("/results".equals(path)) {
            return role == STUDENT_ROLE;
        }
        if (path != null && path.startsWith("/quizzes")) {
            return authorizeQuizRequest(request, role);
        }
        if (path != null && path.startsWith("/questions")) {
            return authorizeQuestionRequest(request, role);
        }
        return "/profile".equals(path) || "/logout".equals(path);
    }

    private boolean authorizeQuizRequest(HttpServletRequest request, int role) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return role == ADMIN_ROLE || role == STUDENT_ROLE;
        }
        String action = request.getParameter("action");
        return "search".equals(action) || (role == ADMIN_ROLE && isOneOf(action,
                "add", "delete", "edit", "create", "update"));
    }

    private boolean authorizeQuestionRequest(HttpServletRequest request, int role) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return role == ADMIN_ROLE || role == STUDENT_ROLE;
        }
        String action = request.getParameter("action");
        if ("run".equals(action)) {
            return role == STUDENT_ROLE;
        }
        return role == ADMIN_ROLE && isOneOf(action,
                "add", "addQuestion", "view", "edit", "editQuestion", "delete");
    }

    private boolean isOneOf(String value, String... allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
