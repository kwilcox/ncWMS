<%@include file="xml_header.jsp"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@taglib uri="/WEB-INF/taglib/wmsUtils" prefix="utils"%> <%-- tag library for useful utility functions --%>
<%-- Displays a calendar and supporting metadata for a variable and particular focus time
     See MetadataController.showCalendar().
     
     Data (models) passed in to this page:
          nearestIndex = nearest index along time axis to the focus time (int)
          variable = The variable in question (VariableMetadata) --%>
<root>
    <%-- Calculate the nearest time to the focus time (in seconds since epoch) --%>
    <c:set var="nearestTime" value="${variable.tvalues[nearestIndex]}"/>
    <nearestValue>${utils:secondsToISO8601(nearestTime)}</nearestValue>
    <%--<prettyNearestValue>%s</prettyNearestValue>" % time.strftime(prettyDateFormat, time.gmtime(tValues[nearestIndex])))--%>
    <nearestIndex>${nearestIndex}</nearestIndex>

    <calendar>
        <table>
            <tbody>
                <%-- Add the navigation buttons at the top of the month view --%>
                <tr>
                    <td><a href="#" onclick="javascript:setCalendar('${variable.dataset.id}','${variable.id}','${utils:getYearBefore(nearestTime)}'); return false">&lt;&lt;</a></td>
                    <td><a href="#" onclick="javascript:setCalendar('${variable.dataset.id}','${variable.id}','${utils:getMonthBefore(nearestTime)}'); return false">&lt;</a></td>
                    <td colspan="3">${utils:getCalendarHeading(nearestTime)}</td>
                    <td><a href="#" onclick="javascript:setCalendar('${variable.dataset.id}','${variable.id}','${utils:getMonthAfter(nearestTime)}'); return false">&gt;</a></td>
                    <td><a href="#" onclick="javascript:setCalendar('${variable.dataset.id}','${variable.id}','${utils:getYearAfter(nearestTime)}'); return false">&gt;&gt;</a></td>
                </tr>
                <%-- Add the day-of-week headings --%>
                <tr><th>M</th><th>T</th><th>W</th><th>T</th><th>F</th><th>S</th><th>S</th></tr>
                <c:forEach var="week" items="${utils:getWeeksOfMonth(nearestTime)}">
                <tr>
                    <c:forEach var="day" items="${week}">
                    <td>
                        <c:if test="${day > 0}">
                        ${day}
                        </c:if>
                    </td>
                    </c:forEach>
                </tr>
                </c:forEach>
    <%--# Add the calendar body
    tValIndex = 0 # index in tvalues array
            if day > 0:
                # Search through the t axis and find out whether we have
                # any data for this particular day
                found = 0
                calendarDay = (nearesttime[0], nearesttime[1], day, 0, 0, 0, 0, 0, 0)
                while not found and tValIndex < len(tValues):
                    axisDay = time.gmtime(tValues[tValIndex])
                    res = _compareDays(axisDay, calendarDay)
                    if res == 0:
                        found = 1 # Found data on this day
                    elif res < 0:
                        tValIndex = tValIndex + 1 # Date on axis is before target day
                    else:
                        break # Date on axis is after target day: no point searching further
                if found:
                    tValue = wmsUtils.secondsToISOString(tValues[tValIndex])
                    prettyTValue = time.strftime(prettyDateFormat, axisDay)
                    <td id="t%d"><a href="#" onclick="javascript:getTimesteps('%s','%s','%d','%s','%s'); return false">%d</a></td>" % (tValIndex, dsId, varID, tValIndex, tValue, prettyTValue, day))
                else:
                    <td>%d</td>" % day)
            else:
                <td></td>") 
                </tr>--%>
            </tbody>
        </table>
    </calendar>
</root>