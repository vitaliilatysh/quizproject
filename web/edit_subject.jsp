<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Edit subject</title>
</head>
<body>
<table>
    <form action="subjects?subjectId=${subject.id}" method="post">
        <input type="text" name="subjectUpdatedName" value=${subject.name}>
        <button class="btn btn-primary btn-block" type="submit">Save</button>
    </form>
</table>
</body>
</html>
