<!DOCTYPE html>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<fmt:setLocale value="${sessionScope.lang}"/>
<fmt:requestEncoding value="UTF-8"/>
<fmt:setBundle basename="messages"/>
<html>
<head>
    <title><fmt:message key="page.edit.question.title"/></title>
    <jsp:include page="header.jsp"/>
    <link href="static/style.css" rel="stylesheet" type="text/css">
</head>

<body>
<nav class="navbar navbar-expand-lg navbar-light bg-light navbar-fixed-top">
    <a class="navbar-brand" href="#"><fmt:message key="page.edit.question.title"/></a>
    <jsp:include page="navbar.jsp"/>
</nav>
<div class="container">
    <form class="needs-validation" action="questions" method="post" novalidate>
        <input hidden name="action" value="editQuestion"/>
        <input type="text" name="quiz" value="${quiz}" hidden>
        <input type="text" name="questionId" value="${questionId}" hidden>
        <div class="form-group">
            <label for="inputQuestion"><fmt:message key="questions.label.text"/></label>
            <textarea class="form-control" name="question" rows=10 id="inputQuestion" required minlength="5"
                      maxlength="250">${question}</textarea>
        </div>
        <div class="form-row">
            <div class="form-group col-md-6">
                <label for="inputAnswerA"><fmt:message key="questions.answer.a"/></label>
                <input class="form-control" type="text" name="answerA" value="${answerA}" id="inputAnswerA" required
                       maxlength="50"
                       pattern=".*\S+.*">
            </div>
            <div class="form-group col-md-6">
                <label for="inputAnswerB"><fmt:message key="questions.answer.b"/></label>
                <input class="form-control" type="text" name="answerB" value="${answerB}" id="inputAnswerB" required
                       maxlength="50"
                       pattern=".*\S+.*">
            </div>
            <div class="form-group col-md-6">
                <label for="inputAnswerC"><fmt:message key="questions.answer.c"/></label>
                <input class="form-control" type="text" name="answerC" value="${answerC}" id="inputAnswerC" required
                       maxlength="50"
                       pattern=".*\S+.*">
            </div>
            <div class="form-group col-md-6">
                <label for="inputAnswerD"><fmt:message key="questions.answer.d"/></label>
                <input class="form-control" type="text" name="answerD" value="${answerD}" id="inputAnswerD" required
                       maxlength="50"
                       pattern=".*\S+.*">
            </div>
        </div>
        <div class="form-group">
            <label><fmt:message key="questions.label.correct"/></label>
            <div class="form-check">
                <c:if test="${correctAnswerA == false}">
                    <input class="form-check-input" name="correctAnswerA" type="checkbox" value="A"
                           id="defaultCheckA">
                </c:if>
                <c:if test="${correctAnswerA == true}">
                    <input class="form-check-input" name="correctAnswerA" type="checkbox" value="A"
                           id="defaultCheckA" checked>
                </c:if>
                <c:if test="${correctAnswerA == null}">
                    <input class="form-check-input" name="correctAnswerA" type="checkbox" value="A"
                           id="defaultCheckA">
                </c:if>
                <label class="form-check-label" for="defaultCheckA"><fmt:message key="questions.correct.a"/></label>
            </div>
            <div class="form-check">
                <c:if test="${correctAnswerB == false}">
                    <input class="form-check-input" name="correctAnswerB" type="checkbox" value="B"
                           id="defaultCheckB">
                </c:if>
                <c:if test="${correctAnswerB == true}">
                    <input class="form-check-input" name="correctAnswerB" type="checkbox" value="B"
                           id="defaultCheckB" checked>
                </c:if>
                <c:if test="${correctAnswerB == null}">
                    <input class="form-check-input" name="correctAnswerB" type="checkbox" value="B"
                           id="defaultCheckB">
                </c:if>
                <label class="form-check-label" for="defaultCheckB"><fmt:message key="questions.correct.b"/></label>
            </div>
            <div class="form-check">
                <c:if test="${correctAnswerC == false}">
                    <input class="form-check-input" name="correctAnswerC" type="checkbox" value="C"
                           id="defaultCheckC">
                </c:if>
                <c:if test="${correctAnswerC == true}">
                    <input class="form-check-input" name="correctAnswerC" type="checkbox" value="C"
                           id="defaultCheckC" checked>
                </c:if>
                <c:if test="${correctAnswerC == null}">
                    <input class="form-check-input" name="correctAnswerC" type="checkbox" value="C"
                           id="defaultCheckC">
                </c:if>
                <label class="form-check-label" for="defaultCheckC"><fmt:message key="questions.correct.c"/> </label>
            </div>
            <div class="form-check">
                <c:if test="${correctAnswerD == false}">
                    <input class="form-check-input" name="correctAnswerD" type="checkbox" value="D"
                           id="defaultCheckD">
                </c:if>
                <c:if test="${correctAnswerD == true}">
                    <input class="form-check-input" name="correctAnswerD" type="checkbox" value="D"
                           id="defaultCheckD" checked>
                </c:if>
                <c:if test="${correctAnswerD == null}">
                    <input class="form-check-input" name="correctAnswerD" type="checkbox" value="D"
                           id="defaultCheckD">
                </c:if>
                <label class="form-check-label" for="defaultCheckD"><fmt:message key="questions.correct.d"/> </label>
            </div>
            <div class="invalidMessage">
                ${checkboxAnswersMessage}
            </div>
        </div>
        <button type="submit" class="btn btn-success"><fmt:message key="button.save"/></button>
    </form>
</div>
</body>
</html>
