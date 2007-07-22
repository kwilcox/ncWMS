<%@include file="xml_header.jsp"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="/WEB-INF/taglib/wmsUtils" prefix="utils"%> <%-- tag library for useful utility functions --%>
<%-- Displays a drop-down box showing the available timesteps for a given date
     See MetadataController.showTimesteps().
     
     Data (models) passed in to this page:
         timesteps = maps ISO8601 date-times to simple HH:mm:ss time strings (Map<String, String>) --%>
<select id="tValues" onchange="javascript:updateMap()">
    <c:forEach var="timestep" items="${timesteps}">
    <option value="${timestep.key}">${timestep.value}</option>
    </c:forEach>
</select>