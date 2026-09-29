<%@ page contentType="text/html;charset=UTF-8" language="java" errorPage="error.jsp" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- Directives above; EL and JSTL below. --%>
<%! private static final String TITLE = "Price History Report"; %>  <%-- JSP declaration --%>
<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title><%= TITLE %></title></head>  <%-- JSP expression --%>
<body>
  <h1><%= TITLE %></h1>
  <c:if test="${not empty error}"><p role="alert" style="color:#b42318"><c:out value="${error}"/></p></c:if>
  <form method="get" action="${pageContext.request.contextPath}/report" onsubmit="return validate(this)">
    <label for="productId">Product id</label>
    <input id="productId" name="productId" maxlength="40" required pattern="[A-Za-z0-9_-]{1,40}" value="<c:out value='${param.productId}'/>">
    <button type="submit">Show report</button>
  </form>
  <script>function validate(f){ return /^[A-Za-z0-9_-]{1,40}$/.test(f.productId.value) || (alert('Use letters, digits, - or _'), false); }</script>
</body></html>
