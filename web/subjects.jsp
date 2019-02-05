<!DOCTYPE html>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Subjects</title>
    <meta content="width=device-width, initial-scale=1.0" name="viewport">
    <link crossorigin="anonymous" href="https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/css/bootstrap.min.css"
          integrity="sha384-MCw98/SFnGE8fJT3GXwEOngsV7Zt27NXFoaoApmYm81iuXoPkFOJwJ8ERdknLPMO" rel="stylesheet">
</head>
<body>
<div class="container">
    <div class="page-header">
        <h1>Subjects</h1>
        <form action="logout" method="post">
            <input type="submit" value="Logout">
        </form>
    </div>
    <table class="table table-responsive">
        <c:forEach var="subject" items="${subjects}">
            <tr>
                <td><a href="quizzes?subject=${subject.id}">${subject.name}</a></td>
                <td>
                    <form action="subjects?subjectId=${subject.id}" method="post">
                        <button type="submit" class="btn btn-danger" name="delete">Delete</button>
                    </form>
                </td>
                <td>
                    <form action="subjects?subjectId=${subject.id}" method="post">
                        <button type="submit" class="btn btn-success" name="edit">Edit</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        <form action="subjects" method="post">
            <input type="text" name="subjectNewName" value=${subjectNewName}>
            <button class="btn btn-info" type="submit">Add</button>
        </form>
    </table>
</div>
</body>
</html>
