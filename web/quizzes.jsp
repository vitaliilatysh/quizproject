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
    <title><fmt:message key="quizzes.title"/></title>
    <jsp:include page="header.jsp"/>
    <c:if test="${sessionScope.role == 1}">
        <script>
            $(document).ready(function () {
                $('#quizzes').DataTable({
                    searching: false,
                    paging: false,
                    "columnDefs": [
                        {"orderable": false, "targets": 5},
                        {"orderable": false, "targets": 6},
                    ]
                })
            });
        </script>
    </c:if>
    <c:if test="${sessionScope.role == 2}">
        <script>
            $(document).ready(function () {
                $('#quizzes').DataTable({
                    searching: false,
                    paging: false,
                    "columnDefs": [
                        {"orderable": false, "targets": 5}
                    ]
                })
            });
        </script>
    </c:if>
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-light bg-light">
    <a class="navbar-brand" href="#"><fmt:message key="quizzes.title"/> </a>
    <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent"
            aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
    </button>
    <div class="collapse navbar-collapse" id="navbarSupportedContent">
        <ul class="navbar-nav mr-auto">
            <li class="nav-item">
                <c:if test="${sessionScope.role == 1}">
                    <form action="quizzes" method="post">
                        <input hidden name="action" value="add"/>
                        <button type="submit" class="btn btn-success"><fmt:message key="button.add"/></button>
                    </form>
                </c:if>
            </li>
        </ul>
        <form action="quizzes" class="form-inline my-2 my-lg-0" method="post">
            <input hidden name="action" value="search">
            <input class="form-control mr-sm-2" type="search" name="subject" placeholder="${search}"
                   value="${subjectName}" aria-label="Search">
            <button class="btn btn-outline-success my-2 my-sm-0" type="submit" hidden>Search</button>
        </form>
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
                        <c:if test="${sessionScope.role == 1}">
                            <a class="dropdown-item" href="allresults"><fmt:message key="header.dropdown.results"/> </a>
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
        <th><fmt:message key="quizzes.table.header.quiz"/></th>
        <th><fmt:message key="quizzes.table.header.complexity"/></th>
        <th><fmt:message key="quizzes.table.header.time"/></th>
        <th><fmt:message key="quizzes.table.header.subject"/></th>
        <th><fmt:message key="quizzes.table.header.questions"/></th>
        <c:if test="${sessionScope.role == 1}">
            <th><fmt:message key="button.edit"/> </th>
            <th><fmt:message key="button.delete"/> </th>
        </c:if>
        <c:if test="${sessionScope.role == 2}">
            <th><fmt:message key="quizzes.table.header.action"/> </th>
        </c:if>
    </tr>
    </thead>
    <c:forEach var="quiz" items="${quizzes}">
        <c:if test="${quiz.totalQuestionsNumber == 0}">
            <c:if test="${sessionScope.role == 1}">
                <tr>
                    <td>${quiz.name}</td>
                    <td>${quiz.complexity}</td>
                    <td>${quiz.timeToPass}</td>
                    <td>${quiz.subjectName}</td>
                    <c:if test="${sessionScope.role == 1}">
                        <td>
                            <form action="questions" method="post">
                                <input hidden name="action" value="view"/>
                                <button type="submit" class="btn btn-link" name="quiz"
                                        value="${quiz.id}">${quiz.totalQuestionsNumber}
                                </button>
                            </form>
                        </td>
                    </c:if>
                    <c:if test="${sessionScope.role == 2}">
                        <td>${quiz.totalQuestionsNumber}</td>
                    </c:if>
                    <c:if test="${sessionScope.role == 1}">
                        <td>
                            <form action="quizzes" method="post">
                                <input hidden name="action" value="edit"/>
                                <input hidden name="quizName" value="${quiz.name}"/>
                                <input hidden name="quizComplexity" value="${quiz.complexity}"/>
                                <input hidden name="quizTime" value="${quiz.timeToPass}"/>
                                <input hidden name="quizSubject" value="${quiz.subjectName}"/>
                                <button type="submit" class="btn btn-primary" name="quiz" value="${quiz.id}">
                                    <fmt:message key="button.edit"/>
                                </button>
                            </form>
                        </td>
                        <td>
                            <form action="quizzes" method="post">
                                <input hidden name="action" value="delete"/>
                                <button type="submit" class="btn btn-danger" name="quiz" value="${quiz.id}"><fmt:message
                                        key="button.delete"/>
                                </button>
                            </form>
                        </td>
                    </c:if>
                    <c:if test="${sessionScope.role == 2}">
                        <td>
                            <c:if test="${quiz.totalQuestionsNumber > 0}">
                                <form action="questions" method="post">
                                    <input hidden name="action" value="run"/>
                                    <button type="submit" class="btn btn-info" name="quiz" value="${quiz.id}">
                                        <fmt:message key="button.start"/>
                                    </button>
                                </form>
                            </c:if>
                        </td>
                    </c:if>
                </tr>
            </c:if>
        </c:if>
        <c:if test="${quiz.totalQuestionsNumber > 0}">
            <tr>
                <td>${quiz.name}</td>
                <td>${quiz.complexity}</td>
                <td>${quiz.timeToPass}</td>
                <td>${quiz.subjectName}</td>
                <c:if test="${sessionScope.role == 1}">
                    <td>
                        <form action="questions" method="post">
                            <input hidden name="action" value="view"/>
                            <button type="submit" class="btn btn-link" name="quiz"
                                    value="${quiz.id}">${quiz.totalQuestionsNumber}
                            </button>
                        </form>
                    </td>
                </c:if>
                <c:if test="${sessionScope.role == 2}">
                    <td>${quiz.totalQuestionsNumber}</td>
                </c:if>
                <c:if test="${sessionScope.role == 1}">
                    <td>
                        <form action="quizzes" method="post">
                            <input hidden name="action" value="edit"/>
                            <input hidden name="quizName" value="${quiz.name}"/>
                            <input hidden name="quizComplexity" value="${quiz.complexity}"/>
                            <input hidden name="quizTime" value="${quiz.timeToPass}"/>
                            <input hidden name="quizSubject" value="${quiz.subjectName}"/>
                            <button type="submit" class="btn btn-primary" name="quiz" value="${quiz.id}"><fmt:message
                                    key="button.edit"/>
                            </button>
                        </form>
                    </td>
                    <td>
                        <form action="quizzes" method="post">
                            <input hidden name="action" value="delete"/>
                            <button type="submit" class="btn btn-danger" name="quiz" value="${quiz.id}"><fmt:message
                                    key="button.delete"/>
                            </button>
                        </form>
                    </td>
                </c:if>
                <c:if test="${sessionScope.role == 2}">
                    <td>
                        <c:if test="${quiz.totalQuestionsNumber > 0}">
                            <form action="questions" method="post">
                                <input hidden name="action" value="run"/>
                                <button type="submit" class="btn btn-info" name="quiz" value="${quiz.id}"><fmt:message
                                        key="button.start"/></button>
                            </form>
                        </c:if>
                    </td>
                </c:if>
            </tr>
        </c:if>
    </c:forEach>
</table>
</body>
</html>
