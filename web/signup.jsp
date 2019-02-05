<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<fmt:setLocale value="${sessionScope.lang}"/>
<fmt:requestEncoding value="UTF-8"/>
<fmt:setBundle basename="messages"/>
<fmt:message key="input.confirmPassword.placeholder" var="placeholder"/>
<!DOCTYPE html>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<head>
    <title><fmt:message key="signup.title"/></title>
    <meta content="width=device-width, initial-scale=1.0" name="viewport">
    <link href="static/style.css" rel="stylesheet" type="text/css">
    <jsp:include page="header.jsp"/>
</head>
<body id="signup-body">
<section class="container-fluid">
    <jsp:include page="language.jsp"/>
    <section class="row justify-content-center">
        <section class="col-lg-3 col-md-6 col-sm-6 col-xs-12">
            <form class="form-container needs-validation" method="post" action="signup" novalidate>
                <h4 class="text-center font-weight-bold"><fmt:message key="signup.title"/></h4>
                <div class="form-group">
                    <input autofocus class="form-control" id="username" name="username" value="${username}" placeholder=<fmt:message key="input.username.placeholder"/> required minlength="5" maxlength="15" pattern="[a-zA-zА-Яа-я0-9]{5,15}"
                           type="text">
                    <div class="invalidMessage">
                        ${usernameMessage}
                    </div>
                </div>
                <div class="form-group">
                    <input class="form-control" id="firstName" name="firstName" value="${firstName}" placeholder=<fmt:message key="input.firstname.placeholder"/> required minlength="1" maxlength="20" pattern="[a-zA-zА-Яа-я]{1,20}"
                           type="text">
                </div>
                <div class="form-group">
                    <input class="form-control" id="lastName" name="lastName" value="${lastName}" placeholder=<fmt:message key="input.lastname.placeholder"/> required minlength="1" maxlength="20" pattern="[a-zA-zА-Яа-я0-9]{1,20}"
                           type="text">
                </div>
                <div class="form-group">
                    <input class="form-control" id="password" name="password" value="${password}" placeholder="<fmt:message key="input.password.placeholder"/>" required minlength="5" maxlength="15" pattern="[^\s]{5,15}"
                           type="password">
                </div>
                <div class="form-group">
                    <input class="form-control" id="confirmPassword" name="confirmPassword" value="${confirmPassword}" placeholder="${placeholder}" required minlength="5" maxlength="15" pattern="[^\s]{5,15}"
                           type="password">
                    <div class="invalidMessage">
                        ${confirmPwMessage}
                    </div>
                </div>
                <button class="btn btn-success btn-block" type="submit"><fmt:message key="button.signup"/></button>
                <a class="btn btn-primary btn-block" href="login" role="button"><fmt:message key="button.submit"/></a>
            </form>
        </section>
    </section>
</section>
</body>
</html>
