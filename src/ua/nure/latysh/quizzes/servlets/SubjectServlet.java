package ua.nure.latysh.quizzes.servlets;

import ua.nure.latysh.quizzes.entities.Subject;
import ua.nure.latysh.quizzes.services.SubjectService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@WebServlet("/subjects")
public class SubjectServlet extends HttpServlet {

    private final SubjectService subjectService;

    public SubjectServlet() {
        this(new SubjectService());
    }

    SubjectServlet(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {

        List<Subject> subjects = subjectService.getAllSubjects();

        request.setAttribute("subjects", subjects);

        request.getRequestDispatcher("/WEB-INF/views/subjects.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (request.getParameter("subjectNewName") != null) {
            String subjectName = request.getParameter("subjectNewName");
            Subject subject = new Subject();
            subject.setName(subjectName);
            subjectService.addSubject(subject);
            response.sendRedirect("subjects");
        } else if(request.getParameter("delete") != null) {

            String subjectId = request.getParameter("subjectId");
            Integer parsedSubjectId = parseId(subjectId, response);
            if (parsedSubjectId == null) {
                return;
            }
            subjectService.findSubjectById(parsedSubjectId).ifPresent(subjectService::deleteSubject);
            response.sendRedirect("subjects");
        } else if(request.getParameter("edit") != null){
            String forward = "/WEB-INF/views/edit_subject.jsp";

            String subjectId = request.getParameter("subjectId");
            Integer parsedSubjectId = parseId(subjectId, response);
            if (parsedSubjectId == null) {
                return;
            }
            request.setAttribute("subject", subjectService.findSubjectById(parsedSubjectId).orElse(null));

            request.getRequestDispatcher(forward).forward(request, response);

        } else if(request.getParameter("subjectId") != null){

            String subjectId = request.getParameter("subjectId");
            String subjectName = request.getParameter("subjectUpdatedName");
            Integer parsedSubjectId = parseId(subjectId, response);
            if (parsedSubjectId == null) {
                return;
            }
            Optional<Subject> subject = subjectService.findSubjectById(parsedSubjectId);
            if (subject.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Subject not found");
                return;
            }
            Subject foundSubject = subject.get();
            foundSubject.setName(subjectName);
            subjectService.updateSubject(foundSubject);

            response.sendRedirect("subjects");

        }
    }

    private Integer parseId(String value, HttpServletResponse response) throws IOException {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid identifier");
            return null;
        }
    }

}

