<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<fmt:setLocale value="${sessionScope.lang}"/>
<fmt:requestEncoding value="UTF-8"/>
<fmt:setBundle basename="messages"/>
<!DOCTYPE html>
<html>
<head>
    <title><fmt:message key="editQuiz.title"/> </title>
    <link href="static/style.css" rel="stylesheet" type="text/css">
    <jsp:include page="header.jsp"/>
</head>

<body>
<nav class="navbar navbar-expand-lg navbar-light bg-light navbar-fixed-top">
    <a class="navbar-brand" href="#"><fmt:message key="editQuiz.title"/> </a>
    <jsp:include page="navbar.jsp"/>
</nav>
<div class="container">
    <form class="needs-validation" action="quizzes" method="post" novalidate>
        <input hidden name="action" value="update"/>
        <input hidden name="quiz" value="${quiz}"/>
        <div class="form-row">
            <div class="form-group col-md-4">
                <label for="inputQuizName"><fmt:message key="addQuiz.label.name"/> </label>
                <input type="text" class="form-control" name="quizName" value="${quizName}" id="inputQuizName" minlength="5" maxlength="50" required pattern=".*\S+.*">
                <div class="invalid-feedback">
                    Please type a quiz name. From 5 to 50 symbols.
                </div>
                <div class="invalidMessage">
                    ${quizNameMessage}
                </div>
            </div>
            <div class="form-group col-md-4">
                <label for="selectSubject"><fmt:message key="addQuiz.lable.subject"/></label>
                <select class="form-control" name="subjectName" id="selectSubject">
                    <c:forEach var="subject" items="${subjects}">
                        <option name="subjectName" value="${subject.name}"
                                <c:if test="${subject.name == quizSubject}">
                                    selected
                                </c:if>
                        >${subject.name}
                        </option>
                    </c:forEach>
                </select>
            </div>
            <div class="form-group col-md-2">
                <label for="selectComplexity"><fmt:message key="addQuiz.label.complexity"/></label>
                <select class="form-control" name="complexity" id="selectComplexity">
                    <c:forEach var="complexity" items="${complexities}">
                        <option name="complexity" value="${complexity.levelName}"
                                <c:if test="${complexity.levelName == quizComplexity}">
                                    selected
                                </c:if>
                        >${complexity.levelName}
                        </option>
                    </c:forEach>
                </select>
            </div>
            <div class="form-group col-md-2">
                <label for="inputTime"><fmt:message key="addQuiz.label.time"/></label>
                <input type="text" class="form-control" name="time" value="${quizTime}" id="inputTime" required minlength="1" maxlength="3" pattern="^0*([1-9]|[1-9][0-9]|1[0-7][0-9]|180)$">
                <div class="invalid-feedback">
                    Please set a quiz time. From 1 to 180 minutes.
                </div>
            </div>
        </div>
        <button type="submit" class="btn btn-success"><fmt:message key="button.save"/></button>
    </form>
</div>
</body>
</html>
