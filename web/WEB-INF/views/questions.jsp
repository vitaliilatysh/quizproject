<!DOCTYPE html>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<fmt:setLocale value="${sessionScope.lang}"/>
<fmt:requestEncoding value="UTF-8"/>
<fmt:setBundle basename="messages"/>
<html>
<head>
    <title>Questions</title>
    <jsp:include page="header.jsp"/>
    <script>
        function startTimer(expiresAt, display) {
            function render() {
                var timer = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
                var minutes = parseInt(timer / 60, 10);
                var seconds = parseInt(timer % 60, 10);

                minutes = minutes < 10 ? "0" + minutes : minutes;
                seconds = seconds < 10 ? "0" + seconds : seconds;

                display.textContent = minutes + " " + ":" + " " + seconds;

                if (timer <= 0) {
                    document.getElementById("submitQuiz").click();
                    return false;
                }
                return true;
            }

            if (render()) {
                var timerId = setInterval(function () {
                    if (!render()) {
                        clearInterval(timerId);
                    }
                }, 1000);
            }
        }

        window.onhashchange = function() {
            document.getElementById("submitQuiz").click();
        };

        window.onload = function () {
            var expiresAt = Number("${sessionScope.quizExpiresAt}");
            var display = document.querySelector('#timer');
            startTimer(expiresAt, display);
        };
    </script>
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-light bg-light fixed-top">
    <a class="navbar-brand" href="#"><fmt:message key="quizzes.table.header.questions"/> </a>
   <jsp:include page="navbar.jsp"/>
</nav>
<div class="container" style="padding-top: 90px; padding-bottom: 100px">
    <form action="results" method="post">
        <input type="hidden" name="_csrf" value="${sessionScope.csrfToken}">
        <c:set var="count" value="0" scope="page"/>
        <c:forEach var="entry" items="${answersPerQuestion}">
            <div class="form-group">
                <c:set var="count" value="${count + 1}" scope="page"/>
                <label>${count}. <c:out value="${entry.key.question}"/></label>
                <c:forEach var="answer" items="${entry.value}">
                    <div class="form-check">
                        <input class="form-check-input" name="answerId" type="checkbox" value="${answer.id}"
                               id="defaultCheck">
                        <label class="form-check-label" for="defaultCheck"><c:out value="${answer.answer}"/></label>
                    </div>
                </c:forEach>
            </div>
        </c:forEach>
        <button id="submitQuiz" type="submit" class="btn btn-success"><fmt:message
                key="button.finish"/></button>
    </form>
</div>
</body>
</html>
