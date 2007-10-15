<%@page contentType="application/json"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib uri="http://www.atg.com/taglibs/json" prefix="json"%>
<%-- Displays the min and max values of data on a certain image
     
     Data (models) passed in to this page:
          minMax = Array of 2 floats with min and max values --%>
<json:object>
    <json:property name="min" value="${minMax[0]}"/>
    <json:property name="max" value="${minMax[1]}"/>
</json:object>