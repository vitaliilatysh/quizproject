<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<fmt:setLocale value="${sessionScope.lang}"/>
<fmt:requestEncoding value="UTF-8"/>
<fmt:setBundle basename="messages"/>
<html>
<head>
    <title><fmt:message key="users.title" /></title>
    <jsp:include page="header.jsp"/>
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-light bg-light navbar-fixed-top">
    <a class="navbar-brand" href="#"><fmt:message key="users.title" /></a>
   <jsp:include page="navbar.jsp"/>
</nav>
<table class="table">
    <thead>
    <tr>
        <th><fmt:message key="input.firstname.placeholder"/> </th>
        <th><fmt:message key="input.lastname.placeholder"/> </th>
        <th><fmt:message key="profile.registration.date"/></th>
        <th><fmt:message key="profile.lastlogin.date"/></th>
        <th><fmt:message key="profile.role"/></th>
        <th><fmt:message key="users.table.header.status"/></th>
        <th>Action</th>
    </tr>
    </thead>
    <c:forEach var="user" items="${users}">
        <tr>
            <td class="align-middle">${user.firstName}</td>
            <td class="align-middle">${user.lastName}</td>
            <td class="align-middle">${user.registerDateTime}</td>
            <td class="align-middle">${user.loginDateTime}</td>
            <td class="align-middle">${user.role}</td>
            <td class="align-middle">
                <c:if test="${user.status == 'active'}">
                    <h4><span class="badge badge-success badge-secondary"><fmt:message key="badge.active"/></span></h4>
                </c:if>
                <c:if test="${user.status == 'blocked'}">
                    <h4><span class="badge badge-danger badge-secondary"><fmt:message key="badge.block"/></span></h4>
                </c:if>
            </td>
            <td class="align-middle">
                <form action="users" method="post">
                    <c:if test="${user.status == 'active'}">
                        <input hidden name="action" value="block"/>
                        <button type="submit" class="btn btn-danger" name="userId" value="${user.id}"><fmt:message key="button.block"/>
                        </button>
                    </c:if>
                    <c:if test="${user.status == 'blocked'}">
                        <input hidden name="action" value="activate"/>
                        <button type="submit" class="btn btn-success" name="userId" value="${user.id}"><fmt:message key="button.unblock"/>
                        </button>
                    </c:if>
                </form>
            </td>
        </tr>
    </c:forEach>
</table>
</body>
</html>
