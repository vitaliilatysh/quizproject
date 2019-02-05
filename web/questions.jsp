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
        function startTimer(duration, display) {
            var timer = duration, minutes, seconds;
            setInterval(function () {
                minutes = parseInt(timer / 60, 10);
                seconds = parseInt(timer % 60, 10);

                minutes = minutes < 10 ? "0" + minutes : minutes;
                seconds = seconds < 10 ? "0" + seconds : seconds;

                display.textContent = minutes + " " + ":" + " " + seconds;

                window.localStorage.setItem("seconds", seconds);
                window.localStorage.setItem("minutes", minutes);

                if (--timer < 0) {
                    localStorage.clear();
                    document.getElementById("submitQuiz").click();
                }
            }, 1000);
        }

        window.onhashchange = function() {
            document.getElementById("submitQuiz").click();
        };

        window.onload = function () {
            var sec = parseInt(window.localStorage.getItem("seconds"));
            var min = parseInt(window.localStorage.getItem("minutes"));

            if (parseInt(min * sec) || parseInt(min * sec) === 0) {
                var fiveMinutes = (parseInt(min * 60) + sec);
            } else {
                fiveMinutes = ${sessionScope.quizTime};
            }
            display = document.querySelector('#timer');
            startTimer(fiveMinutes, display);
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
        <c:set var="count" value="0" scope="page"/>
        <c:forEach var="entry" items="${answersPerQuestion}">
            <div class="form-group">
                <c:set var="count" value="${count + 1}" scope="page"/>
                <label>${count}. ${entry.key.question}</label>
                <c:forEach var="answer" items="${entry.value}">
                    <div class="form-check">
                        <input class="form-check-input" name="answerId" type="checkbox" value="${answer.id}"
                               id="defaultCheck">
                        <label class="form-check-label" for="defaultCheck">${answer.answer}</label>
                    </div>
                </c:forEach>
            </div>
        </c:forEach>
        <button id="submitQuiz" type="submit" onclick="localStorage.clear()" class="btn btn-success"><fmt:message
                key="button.finish"/></button>
    </form>
</div>
</body>
</html>
