# routines that produce output (HTML or XML) for web pages
try:
    from cStringIO import StringIO
except ImportError:
    from StringIO import StringIO
import sys, time, math, calendar

if sys.platform.startswith("java"):
    # We're running on Jython
    prefix = "WMS.py"
else:
    # TODO: check for presence of CDAT
    prefix = "wms"
import iso8601
import wmsUtils
import getmap

def getMetadata(req, config):
    """ Processes a request for metadata from the Godiva2 web interface """
    params = wmsUtils.RequestParser(req.args)
    metadataItem = params.getParamValue("item", "frontpage")
    if metadataItem == "frontpage":
        req.content_type = "text/html"
        req.write(getFrontPage(config))
    elif metadataItem == "datasets":
        filter = params.getParamValue("filter")
        req.write(getDatasetsDiv(config, filter))
    elif metadataItem == "variables":
        req.content_type = "text/xml"
        dsId = params.getParamValue("dataset")
        req.write(getVariables(config, dsId))
    elif metadataItem == "variableDetails":
        req.content_type = "text/xml"
        dataset = params.getParamValue("dataset")
        varID = params.getParamValue("variable")
        req.write(getVariableDetails(config, dataset, varID))
    elif metadataItem == "calendar":
        req.content_type = "text/xml"
        dataset = params.getParamValue("dataset")
        varID = params.getParamValue("variable")
        dateTime = params.getParamValue("dateTime")
        req.write(getCalendar(config, dataset, varID, dateTime))
    elif metadataItem == "timesteps":
        req.content_type = "text/xml"
        dataset = params.getParamValue("dataset")
        varID = params.getParamValue("variable")
        tIndex = int(params.getParamValue("tIndex"))
        req.write(getTimesteps(config, dataset, varID, tIndex))
    elif metadataItem == "minmax":
        req.content_type = "text/xml"
        dataset = params.getParamValue("dataset")
        varID = params.getParamValue("variable")
        req.write(getMinMax(config, dataset, varID, params))        

def getFrontPage(config):
    """ Returns a front page for the WMS, containing example links """
    doc = StringIO()
    doc.write("<html><head><title>%s</title></head>" % config.server.title)
    doc.write("<body><h1>%s</h1>" % config.server.title)
    doc.write("<p><a href=\"" + prefix + "?SERVICE=WMS&REQUEST=GetCapabilities\">Capabilities document</a></p>")
    doc.write("<p><a href=\"./godiva2.html\">Godiva2 interface</a></p>")
    doc.write("<p><a href=\"./admin/\">Admin interface</a> (requires login)</p>")
    doc.write("<h2>Datasets:</h2>")
    # Print a GetMap link for every dataset we have
    doc.write("<table border=\"1\"><tbody>")
    doc.write("<tr><th>Dataset</th>")
    for format in getmap.getSupportedImageFormats():
        doc.write("<th>%s</th>" % format)
    if config.server.allowFeatureInfo:
        doc.write("<th>FeatureInfo</th>")
    doc.write("</tr>")
    datasets = config.datasets
    for ds in datasets.keys():
        if datasets[ds].ready:
            doc.write("<tr><th>%s</th>" % datasets[ds].title)
            vars = datasets[ds].variables
            for format in getmap.getSupportedImageFormats():
                doc.write("<td>")
                for varID in vars.keys():
                    doc.write("<a href=\"%s?SERVICE=WMS&REQUEST=GetMap&VERSION=1.3.0&STYLES=&CRS=CRS:84&WIDTH=256&HEIGHT=256&FORMAT=%s&TRANSPARENT=true" % (prefix, format))
                    doc.write("&LAYERS=%s%s%s" % (ds, wmsUtils.getLayerSeparator(), varID))
                    bbox = vars[varID].bbox
                    doc.write("&BBOX=%s,%s,%s,%s" % tuple([str(b) for b in bbox]))
                    tvals = vars[varID].tvalues
                    if len(tvals) > 0:
                        doc.write("&TIME=%s" % iso8601.tostring(tvals[-1]))
                    doc.write("\">%s</a><br />" % vars[varID].title)
                doc.write("</td>")
            if config.server.allowFeatureInfo:
                doc.write("<td>")
                if datasets[ds].queryable:
                    for varID in vars.keys():
                        doc.write("<a href=\"%s?SERVICE=WMS&REQUEST=GetFeatureInfo&VERSION=1.3.0&CRS=CRS:84&WIDTH=256&HEIGHT=256&INFO_FORMAT=text/xml" % prefix)
                        doc.write("&QUERY_LAYERS=%s%s%s" % (ds, wmsUtils.getLayerSeparator(), varID))
                        bbox = vars[varID].bbox
                        doc.write("&BBOX=%s,%s,%s,%s" % tuple([str(b) for b in bbox]))
                        doc.write("&I=128&J=128")
                        tvals = vars[varID].tvalues
                        if len(tvals) > 0:
                            doc.write("&TIME=%s" % iso8601.tostring(tvals[-1]))
                        doc.write("\">%s</a><br />" % vars[varID].title)
                else:
                    doc.write("Dataset not queryable")
                doc.write("</td>")
            doc.write("</tr>")
    doc.write("</tbody></table>")
    doc.write("</body></html>")
    s = doc.getvalue()
    doc.close()
    return s

def getDatasetsDiv(config, filter=""):
    """ returns a string with a set of divs representing the datasets.
        Quick and dirty. Only returns divs that start with the filter
        string """
    str = StringIO()
    datasets = config.datasets
    for ds in datasets.keys():
        if ds.startswith(filter):
            str.write("<div id=\"%sDiv\">" % ds)
            str.write("<div id=\"%s\">%s</div>" % (ds, datasets[ds].title))
            str.write("<div id=\"%sContent\">" % ds)
            str.write("Variables in the %s dataset will appear here" % datasets[ds].title)
            str.write("</div>")
            str.write("</div>")
    s = str.getvalue()
    str.close()
    return s

def getVariables(config, dsId):
    """ returns an HTML table containing a set of variables for the given dataset. """
    str = StringIO()
    str.write("<table cellspacing=\"0\"><tbody>")
    vars = config.datasets[dsId].variables
    for varID in vars.keys():
        str.write("<tr><td>")
        str.write("<a href=\"#\" onclick=\"javascript:variableSelected('%s', '%s')\">%s</a>" % (dsId, varID, vars[varID].title))
        str.write("</td></tr>")
    str.write("</tbody></table>")
    s = str.getvalue()
    str.close()
    return s

def getVariableDetails(config, dsId, varID):
    """ returns an XML document containing the details of the given variable
        in the given dataset. """
    s = StringIO()
    var = config.datasets[dsId].variables[varID]
    s.write("<variableDetails dataset=\"%s\" variable=\"%s\" units=\"%s\">" % (dsId, var.title, var.units))
    s.write("<axes>")
    if var.zvalues is not None:
        s.write("<axis type=\"z\" units=\"%s\" positive=\"%d\">" % (var.zunits, var.zpositive))
        for z in var.zvalues:
            s.write("<value>%f</value>" % math.fabs(z))
        s.write("</axis>")
    s.write("</axes>")
    s.write("<range><min>%f</min><max>%f</max></range>" % (var.validMin, var.validMax))
    s.write("<bbox>%s,%s,%s,%s</bbox>" % tuple([str(f) for f in var.bbox]))
    s.write("</variableDetails>")
    doc = s.getvalue()
    s.close()
    return doc

def getCalendar(config, dsId, varID, dateTime):
    """ returns an HTML calendar for the given dataset and variable.
        dateTime is a string in ISO 8601 format with the required
        'focus time' """
    # Get an array of time axis values in seconds since the epoch
    tValues = config.datasets[dsId].variables[varID].tvalues
    # TODO: is this the right thing to do here?
    if len(tValues) == 0:
        return ""
    str = StringIO()
    prettyDateFormat = "%d %b %Y"

    # Find the closest time step to the given dateTime value
    # TODO: binary search would be more efficient
    reqTime = iso8601.parse(dateTime) # Gives seconds since the epoch
    diff = 1e20
    for i in xrange(len(tValues)):
        testDiff = math.fabs(tValues[i] - reqTime)
        if testDiff < diff:
            # Axis is monotonic so we should move closer and closer
            # to the nearest value
            diff = testDiff
            nearestIndex = i
        elif i > 0:
            # We've moved past the closest date
            break
    
    str.write("<root>")
    str.write("<nearestValue>%s</nearestValue>" % iso8601.tostring(tValues[nearestIndex]))
    str.write("<prettyNearestValue>%s</prettyNearestValue>" % time.strftime(prettyDateFormat, time.gmtime(tValues[nearestIndex])))
    str.write("<nearestIndex>%d</nearestIndex>" % nearestIndex)

    # create a struct_time tuple with zero timezone offset (i.e. GMT)
    nearesttime = time.gmtime(tValues[nearestIndex])

    # Now print out the calendar in HTML
    str.write("<calendar>")
    str.write("<table><tbody>")
    # Add the navigation buttons at the top of the month view
    str.write("<tr>")
    str.write("<td><a href=\"#\" onclick=\"javascript:setCalendar('%s','%s','%s'); return false\">&lt;&lt;</a></td>" % (dsId, varID, _getYearBefore(nearesttime)))
    str.write("<td><a href=\"#\" onclick=\"javascript:setCalendar('%s','%s','%s'); return false\">&lt;</a></td>" % (dsId, varID, _getMonthBefore(nearesttime)))
    str.write("<td colspan=\"3\">%s</td>" % _getHeading(nearesttime))
    str.write("<td><a href=\"#\" onclick=\"javascript:setCalendar('%s','%s','%s'); return false\">&gt;</a></td>" % (dsId, varID, _getMonthAfter(nearesttime)))
    str.write("<td><a href=\"#\" onclick=\"javascript:setCalendar('%s','%s','%s'); return false\">&gt;&gt;</a></td>" % (dsId, varID, _getYearAfter(nearesttime)))
    str.write("</tr>")
    # Add the day-of-week headings
    str.write("<tr><th>M</th><th>T</th><th>W</th><th>T</th><th>F</th><th>S</th><th>S</th></tr>")
    # Add the calendar body
    tValIndex = 0 # index in tvalues array
    for week in calendar.monthcalendar(nearesttime[0], nearesttime[1]):
        str.write("<tr>")
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
                    tValue = iso8601.tostring(tValues[tValIndex])
                    prettyTValue = time.strftime(prettyDateFormat, axisDay)
                    str.write("<td id=\"t%d\"><a href=\"#\" onclick=\"javascript:getTimesteps('%s','%s','%d','%s','%s'); return false\">%d</a></td>" % (tValIndex, dsId, varID, tValIndex, tValue, prettyTValue, day))
                else:
                    str.write("<td>%d</td>" % day)
            else:
                str.write("<td></td>")
        str.write("</tr>")

    str.write("</tbody></table>")
    str.write("</calendar>")
    str.write("</root>")

    s = str.getvalue()
    str.close()
    return s

def _getHeading(date):
    """ Returns a string, e.g. "Oct 2006" for the given date """
    month = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[date[1] - 1]
    return "%s %d" % (month, date[0])

def _getYearBefore(date):
    """ Returns an ISO8601-formatted date that is exactly one year earlier than
        the given date """
    # Get the tuple of year, month, day etc
    newDate = tuple([date[0] - 1] + list(date[1:]))
    return iso8601.tostring(time.mktime(newDate))

def _getYearAfter(date):
    """ Returns an ISO8601-formatted date that is exactly one year later than
        the given date """
    # Get the tuple of year, month, day etc
    newDate = tuple([date[0] + 1] + list(date[1:]))
    return iso8601.tostring(time.mktime(newDate))

def _getMonthBefore(date):
    """ Returns an ISO8601-formatted date that is exactly one month earlier than
        the given date """
    if date[1] == 1:
        month = 12
        year = date[0] - 1
    else:
        month = date[1] - 1
        year = date[0]
    newDate = tuple([year] + [month] + list(date[2:]))
    return iso8601.tostring(time.mktime(newDate))

def _getMonthAfter(date):
    """ Returns an ISO8601-formatted date that is exactly one month later than
        the given date """
    if date[1] == 12:
        month = 1
        year = date[0] + 1
    else:
        month = date[1] + 1
        year = date[0]
    newDate = tuple([year] + [month] + list(date[2:]))
    return iso8601.tostring(time.mktime(newDate))

def getTimesteps(config, dsId, varID, tIndex):
    """ Returns an HTML select box allowing the user to select a 
        set of times for a given day """
    datasets = config.datasets
    # Get an array of time axis values in seconds since the epoch
    tValues = config.datasets[dsId].variables[varID].tvalues
    # TODO: is this the right thing to do here?
    if tValues is None:
        return ""
    str = StringIO()
    
    # We find a list of indices of timesteps that are on the same day
    # as the provided tIndex
    indices = {}
    reftime = time.gmtime(tValues[tIndex])
    indices[tIndex] = "%02d:%02d:%02d" % (reftime[3], reftime[4], reftime[5])
    for i in xrange(tIndex + 1, len(tValues)):
        t = time.gmtime(tValues[i])
        diff = _compareDays(reftime, t)
        if diff == 0:
            indices[i] = "%02d:%02d:%02d" % (t[3], t[4], t[5])
        else:
            break
    for i in xrange(tIndex - 1, -1, -1): # count backwards through the axis indices to zero
        t = time.gmtime(tValues[i])
        diff = _compareDays(reftime, t)
        if diff == 0:
            indices[i] = "%02d:%02d:%02d" % (t[3], t[4], t[5])
        else:
            break

    # Write the selection box with the timesteps
    str.write("<select id=\"tValues\" onchange=\"javascript:updateMap()\">")
    keys = indices.keys()
    keys.sort()
    for i in keys:
        str.write("<option value=\"%s\">%s</option>" % (iso8601.tostring(tValues[i]), indices[i]))
    str.write("</select>")

    s = str.getvalue()
    str.close()
    return s

def _compareDays(d1, d2):
    """ Both arguments are struct_time tuples.  Returns 0 if both dates fall
        on the same day.  Returns -1 if d1 falls before d2 and +1 if d1 falls
        after d2 """
    if d1[0] == d2[0] and d1[1] == d2[1] and d1[2] == d2[2]:
        return 0
    else:
        d1s = time.mktime(d1)
        d2s = time.mktime(d2)
        if d1s < d2s:
            return -1
        else:
            return 1

def getMinMax(config, dsID, varID, params):
    
    var = config.datasets[dsID].vars[varID]
    tIndex = getmap._getTIndices(var, params)
    #zValue = 
    # Now read the data
    picData = dataset.read(var, tIndex, zValue, grid.latValues, grid.lonValues, _getFillValue())
    # Now find the minimum and maximum values
    