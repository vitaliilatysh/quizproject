<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<fmt:setLocale value="${sessionScope.lang}"/>
<fmt:requestEncoding value="UTF-8"/>
<fmt:setBundle basename="messages"/>
<!DOCTYPE html>
<html>
<head>
    <title><fmt:message key="profile.title"/> </title>
    <jsp:include page="header.jsp"/>
    <link href="static/style.css" rel="stylesheet" type="text/css">
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-light bg-light navbar-fixed-top">
    <a class="navbar-brand" href="#"><fmt:message key="profile.title"/></a>
    <jsp:include page="navbar.jsp"/>
</nav>
<div class="container">
    <table class="table" id="tprofile">
        <tr>
            <td><fmt:message key="input.firstname.placeholder"/></td>
            <td>${user.firstName}</td>
        </tr>
        <tr>
            <td><fmt:message key="input.lastname.placeholder"/></td>
            <td>${user.lastName}</td>
        </tr>
        <tr>
            <td><fmt:message key="profile.role"/></td>
            <td>${user.role}</td>
        </tr>
        <tr>
            <td><fmt:message key="profile.registration.date"/></td>
            <td>${user.registerDateTime}</td>
        </tr>
        <tr>
            <td><fmt:message key="profile.lastlogin.date"/></td>
            <td>${user.loginDateTime}</td>
        </tr>
    </table>
</div>
</body>
</html>
