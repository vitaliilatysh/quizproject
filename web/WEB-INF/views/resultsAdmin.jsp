<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<fmt:setLocale value="${sessionScope.lang}"/>
<fmt:requestEncoding value="UTF-8"/>
<fmt:setBundle basename="messages"/>
<!DOCTYPE html>
<html>
<head>
    <title><fmt:message key="results.title"/> </title>
    <jsp:include page="header.jsp"/>
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-light bg-light navbar-fixed-top">
    <a class="navbar-brand" href="#"><fmt:message key="results.title"/></a>
    <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent"
            aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
    </button>
    <div class="collapse navbar-collapse" id="navbarSupportedContent">
        <ul class="navbar-nav">
            <li class="nav-item active">
                <a class="nav-link" href="quizzes"><fmt:message key="header.home"/> <span
                        class="sr-only">(current)</span></a>
            </li>
        </ul>
        <ul class="navbar-nav ml-auto">
            <form action="allresults" class="form-inline my-2 my-lg-0" method="post">
                <input hidden name="action" value="search">
                <input class="form-control mr-sm-2" type="datetime-local" name="startRange"
                       value="${startRange}" aria-label="Search">
                <input class="form-control mr-sm-2" type="datetime-local" name="endRange"
                       value="${endRange}" aria-label="Search">
                <button class="btn btn-outline-success my-2 my-sm-0" type="submit" hidden>Search</button>
            </form>
            <form class="form-inline my-2 my-lg0 ml-auto">
                <c:if test="${sessionScope.role == 2}">
                    <li class="nav-item active" style="padding-right: 10px">
                        <h4><span id="timer" class="badge badge-info badge-secondary"></span></h4>
                    </li>
                </c:if>
            </form>
            <form action="" style="width: auto;" class="form-inline my-2 my-lg0 ml-auto">
                <select name="lang" class="custom-select custom-select-md" onchange="this.form.submit()">
                    <option selected>${sessionScope.lang}</option>
                    <c:if test="${sessionScope.lang == 'en'}">
                        <option value="ru">ru</option>
                    </c:if>
                    <c:if test="${sessionScope.lang == 'ru'}">
                        <option value="en">en</option>
                    </c:if>
                </select>
            </form>
            <form action="logout" method="post" class="form-inline my-2 my-lg0 ml-auto">
                <input hidden name="action" value="add"/>
                <li class="nav-item dropdown">
                    <a class="nav-link dropdown-toggle" href="#" id="navbarDropdown" role="button"
                       data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                        ${sessionScope.user.firstName} ${sessionScope.user.lastName}
                    </a>
                    <div class="dropdown-menu" aria-labelledby="navbarDropdown">
                        <a class="dropdown-item" href="profile"><fmt:message key="header.dropdown.profile"/> </a>
                        <div class="dropdown-divider"></div>
                        <c:if test="${sessionScope.role == 1}">
                            <a class="dropdown-item" href="users"><fmt:message key="header.dropdown.users"/> </a>
                        </c:if>
                        <c:if test="${sessionScope.role == 1}">
                            <a class="dropdown-item" href="allresults"><fmt:message key="header.dropdown.results"/> </a>
                        </c:if>
                        <c:if test="${sessionScope.role == 2}">
                            <a class="dropdown-item" href="results"><fmt:message key="header.dropdown.results"/> </a>
                        </c:if>
                    </div>
                </li>

                <button type="submit" onclick="localStorage.clear();" class="btn btn-outline-default my-2 my-sm-0">
                    <fmt:message
                            key="button.logout"/></button>
            </form>
        </ul>
    </div></nav>
<table class="table">
    <thead>
    <tr>
        <th><fmt:message key="results.table.header.username"/></th>
        <th><fmt:message key="results.table.header.quiz"/></th>
        <th><fmt:message key="results.table.header.score"/></th>
        <th><fmt:message key="results.table.header.finishDate"/></th>
        <th><fmt:message key="results.table.header.status"/></th>
    </tr>
    </thead>
    <c:forEach var="result" items="${userResults}">
        <tr>
            <td>${result.username}</td>
            <td>${result.quizName}</td>
            <td>${result.quizScore}%</td>
            <td>${result.endTime}</td>
            <td>
                <c:if test="${result.quizScore >= 75}">
                    <h4><span class="badge badge-success badge-secondary"><fmt:message key="badge.passed"/></span></h4>
                </c:if>
                <c:if test="${result.quizScore < 75}">
                    <h4> <span class="badge badge-danger badge-secondary"><fmt:message key="badge.fail"/></span></h4>
                </c:if>
            </td>
        </tr>
    </c:forEach>
</table>
</body>
</html>
