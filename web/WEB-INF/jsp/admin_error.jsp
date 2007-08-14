<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">
<%-- Display of the error associated with a dataset
     Data (models) passed in to this page:
         dataset     = uk.ac.rdg.resc.ncwms.config.Dataset --%>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Error with dataset ${dataset.id}</title>
    </head>
    <body>

    <h1>Error report for dataset ${dataset.id} (for debugging)</h1>
    
    <c:choose>
        <c:when test="${empty dataset.exception}">
            This dataset does not contain any errors
        </c:when>
        <c:otherwise>
            <b>Stack trace:</b><br />
            ${dataset.exception.class.name}: ${dataset.exception.message}<br />
            <c:forEach var="stacktraceelement" items="${dataset.exception.stackTrace}">
            ${stacktraceelement}<br />
            </c:forEach>
        </c:otherwise>
    </c:choose>
        
    </body>
</html>
