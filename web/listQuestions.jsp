<!DOCTYPE html>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<fmt:setLocale value="${sessionScope.lang}"/>
<fmt:requestEncoding value="UTF-8"/>
<fmt:setBundle basename="messages"/>
<fmt:message key="quizzes.search.placeholder" var="search"/>
<html>
<head>
    <title><fmt:message key="page.list.questions.title"/></title>
    <jsp:include page="header.jsp"/>
        <script>
            $(document).ready(function () {
                $('#quizzes').DataTable({
                    searching: false,
                    paging: false,
                    "columnDefs": [
                        {"orderable": false, "targets": 1},
                        {"orderable": false, "targets": 2}
                    ]
                })
            });
        </script>
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-light bg-light">
    <a class="navbar-brand" href="#"><fmt:message key="page.list.questions.title"/> </a>
    <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent"
            aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
    </button>
    <ul class="navbar-nav">
        <li class="nav-item active">
            <a class="nav-link" href="quizzes"><fmt:message key="header.home"/> <span
                    class="sr-only">(current)</span></a>
        </li>
    </ul>
    <div class="collapse navbar-collapse" id="navbarSupportedContent">
        <ul class="navbar-nav mr-auto">
            <li class="nav-item">
                <c:if test="${sessionScope.role == 1}">
                    <form action="questions" method="post">
                        <input hidden name="action" value="add"/>
                        <input hidden name="quiz" value="${quiz}"/>
                        <button type="submit" class="btn btn-success"><fmt:message key="button.add"/></button>
                    </form>
                </c:if>
            </li>
        </ul>
        <jsp:include page="language.jsp"/>
        <form action="logout" method="post" class="form-inline my-2 my-lg0">
            <input hidden name="action" value="add"/>
            <ul class="navbar-nav mr-auto">
                <li class="nav-item dropdown">
                    <a class="nav-link dropdown-toggle" href="#" id="navbarDropdown" role="button"
                       data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                        ${sessionScope.user.firstName} ${sessionScope.user.lastName}
                    </a>
                    <div class="dropdown-menu" aria-labelledby="navbarDropdown">
                        <a class="dropdown-item" href="profile"><fmt:message key="header.dropdown.profile"/></a>
                        <div class="dropdown-divider"></div>
                        <c:if test="${sessionScope.role == 1}">
                            <a class="dropdown-item" href="users"><fmt:message key="header.dropdown.users"/></a>
                        </c:if>
                        <c:if test="${sessionScope.role == 2}">
                            <a class="dropdown-item" href="results"><fmt:message key="header.dropdown.results"/> </a>
                        </c:if>
                    </div>
                </li>
            </ul>
            <button type="submit" onclick="localStorage.clear();" class="btn btn-outline-default my-2 my-sm-0">
                <fmt:message key="button.logout"/>
            </button>
        </form>
    </div>
</nav>
<table id="quizzes" class="table">
    <thead>
    <tr>
        <th><fmt:message key="questions.label.text"/></th>
        <th><fmt:message key="button.edit"/></th>
        <th><fmt:message key="button.delete"/></th>
    </tr>
    </thead>
    <c:forEach var="question" items="${questions}">
        <tr>
            <td>${question.question}</td>
            <td>
                <form action="questions" method="post">
                    <input hidden name="action" value="edit"/>
                    <button type="submit" class="btn btn-primary" name="question" value="${question.id}">
                        <fmt:message key="button.edit"/>
                    </button>
                </form>
            </td>
            <td>
                <form action="questions" method="post">
                    <input hidden name="action" value="delete"/>
                    <button type="submit" class="btn btn-danger" name="question" value="${question.id}"><fmt:message
                            key="button.delete"/>
                    </button>
                </form>
            </td>
        </tr>
    </c:forEach>
</table>
</body>
</html>
