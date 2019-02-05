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
    <jsp:include page="navbar.jsp"/>
</nav>
<table class="table">
    <thead>
    <tr>
        <th><fmt:message key="results.table.header.attempt"/></th>
        <th><fmt:message key="results.table.header.quiz"/></th>
        <th><fmt:message key="results.table.header.score"/></th>
        <th><fmt:message key="results.table.header.finishDate"/></th>
        <th><fmt:message key="results.table.header.status"/></th>
    </tr>
    </thead>
    <c:forEach var="result" items="${userResults}">
        <tr>
            <td>${result.attemptId}</td>
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
