<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<fmt:setLocale value="${sessionScope.lang}"/>
<fmt:requestEncoding value="UTF-8"/>
<fmt:setBundle basename="messages"/>
<!DOCTYPE html>
<html>
<head>
    <title><fmt:message key="login.header"/></title>
    <meta content="width=device-width, initial-scale=1.0" name="viewport">
    <link href="static/style.css" rel="stylesheet" type="text/css">
    <jsp:include page="header.jsp"/>
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js"></script>
</head>
<body id="login-body">
<section class="container-fluid">
    <jsp:include page="language.jsp"/>
    <section class="row justify-content-center">
        <section class="col-lg-3 col-md-6 col-sm-6 col-xs-12">
            <form class="form-container needs-validation" method="post" action="login" novalidate>
                <h4 class="text-center font-weight-bold"><fmt:message key="login.header"/></h4>
                <div class="form-group">
                    <input autofocus class="form-control" id="username" name="username" placeholder=
                    <fmt:message key="input.username.placeholder"/> required
                           minlength="5" maxlength="15" pattern="[a-zA-zА-Яа-я0-9]{5,15}"
                           type="text">
                    <div class="invalidMessage" onkeyup="document.getElementById('username').style.display = 'none'">
                        ${loginMessage}
                    </div>
                </div>
                <div class="form-group">
                    <input class="form-control" id="password" name="password" placeholder=
                    <fmt:message key="input.password.placeholder"/>
                            onkeyup="this.classList.remove('is-invalid')" required minlength="5" maxlength="15"
                           pattern="[^\s]{5,15}"
                           type="password">
                    <div class="invalidMessage">
                        ${passwordMessage}
                    </div>
                </div>
                <button class="btn btn-primary btn-block" type="submit"><fmt:message key="button.submit"/></button>
                <a class="btn btn-success btn-block" href="signup" role="button"><fmt:message key="button.signup"/> </a>
            </form>
        </section>
    </section>
</section>
</body>
</html>
