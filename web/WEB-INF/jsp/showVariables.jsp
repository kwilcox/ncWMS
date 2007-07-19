<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%-- Displays the variables of a dataset as an HTML table.
     See MetadataController.showVariables().  This is tightly coupled
     with the javascript code in godiva2.js, which is bad.
     
     Data (models) passed in to this page:
         dataset = Dataset containing the variables --%>

 <table cellspacing="0">
     <tbody>
         <c:forEach var="variable" items="${dataset.variables}">
         <tr><td><a href="#" onclick="javascript:variableSelected('${dataset.id}', '${variable.key}')">${variable.value.title}</a></td></tr>
         </c:forEach>
     </tbody>
 </table>