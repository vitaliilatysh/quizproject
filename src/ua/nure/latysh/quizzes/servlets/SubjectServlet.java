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

@WebServlet("/subjects")
public class SubjectServlet extends HttpServlet {

    private SubjectService subjectService = new SubjectService();

    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {

        List<Subject> subjects = subjectService.getAllSubjects();

        request.setAttribute("subjects", subjects);

        request.getRequestDispatcher("subjects.jsp").forward(request, response);
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
            Subject subjectToDelete = subjectService.findSubjectById(Integer.parseInt(subjectId));

            if (subjectToDelete != null) {
                subjectService.deleteSubject(subjectToDelete);
            }
            response.sendRedirect("subjects");
        } else if(request.getParameter("edit") != null){
            String forward = "edit_subject.jsp";

            String subjectId = request.getParameter("subjectId");
            Subject editSubject = subjectService.findSubjectById(Integer.parseInt(subjectId));

            request.setAttribute("subject", editSubject);

            request.getRequestDispatcher(forward).forward(request, response);

        } else if(request.getParameter("subjectId") != null){

            String subjectId = request.getParameter("subjectId");
            String subjectName = request.getParameter("subjectUpdatedName");
            Subject foundSubject = subjectService.findSubjectById(Integer.parseInt(subjectId));
            foundSubject.setName(subjectName);
            subjectService.updateSubject(foundSubject);

            response.sendRedirect("subjects");

        }
    }

}

