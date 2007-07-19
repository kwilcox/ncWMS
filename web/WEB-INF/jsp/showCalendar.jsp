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

<%--    # create a struct_time tuple with zero timezone offset (i.e. GMT)
    nearesttime = time.gmtime(tValues[nearestIndex])

    # Now print out the calendar in HTML --%>
    <calendar>
        <table>
            <tbody>
                <%-- Add the navigation buttons at the top of the month view --%>
                <tr>
                    <td><a href="#" onclick="javascript:setCalendar('${variable.dataset.id}','${variable.id}','%s'); return false">&lt;&lt;</a></td> <%-- % (_getYearBefore(nearesttime)))0--%>
                    <td><a href="#" onclick="javascript:setCalendar('${variable.dataset.id}','${variable.id}','%s'); return false">&lt;</a></td>" % (dsId, varID, _getMonthBefore(nearesttime)))
                    <td colspan="3">${utils:getHeading(nearestTime)}</td>
                    <td><a href="#" onclick="javascript:setCalendar('${variable.dataset.id}','${variable.id}','%s'); return false">&gt;</a></td>" % (dsId, varID, _getMonthAfter(nearesttime)))
                    <td><a href="#" onclick="javascript:setCalendar('${variable.dataset.id}','${variable.id}','%s'); return false">&gt;&gt;</a></td>" % (dsId, varID, _getYearAfter(nearesttime)))
                </tr>
                <%-- Add the day-of-week headings --%>
                <tr><th>M</th><th>T</th><th>W</th><th>T</th><th>F</th><th>S</th><th>S</th></tr>
    <%--# Add the calendar body
    tValIndex = 0 # index in tvalues array
    for week in calendar.monthcalendar(nearesttime[0], nearesttime[1]):
        <tr>")
        for day in week:
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