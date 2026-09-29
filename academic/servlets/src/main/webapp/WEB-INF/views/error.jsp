<%@ page contentType="text/html;charset=UTF-8" isErrorPage="true" %>
<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>Something went wrong</title></head>
<body><h1>Something went wrong</h1><p>The page could not be shown. Check the input and try again.</p>
<%-- No exception message or stack trace is printed to the browser. --%>
<p><a href="${pageContext.request.contextPath}/report">Back to the report form</a></p></body></html>
