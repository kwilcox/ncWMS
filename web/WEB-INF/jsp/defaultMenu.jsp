<%@page contentType="application/json"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib uri="/WEB-INF/taglib/MenuMaker" prefix="menu"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%
response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
response.setHeader("Pragma","no-cache"); //HTTP 1.0
response.setDateHeader ("Expires", 0); //prevents caching at the proxy server
%>
<%--
     Displays the hierarchy of layers from this server as a JSON object
     See MetadataController.showLayerHierarchy().
     
     Data (models) passed in to this page:
         serverTitle = String: title for this server
         datasets = Map<String, Dataset>: all the datasets in this server
         serverInfo  = uk.ac.rdg.resc.ncwms.config.Server object
--%>
<menu:folder label="${serverTitle}">
    <c:forEach items="${datasets}" var="dataset">
        <menu:dataset dataset="${dataset.value}"/>
    </c:forEach>
</menu:folder>