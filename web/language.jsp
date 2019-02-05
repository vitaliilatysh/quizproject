<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<form id="lang-select" action="">
    <%--<input hidden name="url" value="${pageContext.request.requestURL}"/>--%>
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