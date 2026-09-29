<%@ page contentType="text/html;charset=UTF-8" language="java" errorPage="error.jsp" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>Report for ${report.productId}</title></head>
<body>
  <h1>Price history: <c:out value="${report.productId}"/></h1>
  <c:choose>
    <c:when test="${report.count == 0}"><p>No price checks recorded for this product yet.</p></c:when>
    <c:otherwise>
      <p>Lowest ${report.lowest} · Highest ${report.highest} · Latest ${report.latest} · ${report.count} checks</p>
      <table border="1" cellpadding="6">
        <tr><th>Checked</th><th>Price</th><th>Source</th></tr>
        <c:forEach var="row" items="${report.rows}">
          <tr><td>${row.observedAt}</td><td><fmt:formatNumber value="${row.price}" minFractionDigits="2"/> ${row.currency}</td><td><c:out value="${row.source}"/></td></tr>
        </c:forEach>
      </table>
    </c:otherwise>
  </c:choose>
  <p><a href="${pageContext.request.contextPath}/report">Another product</a></p>
</body></html>
