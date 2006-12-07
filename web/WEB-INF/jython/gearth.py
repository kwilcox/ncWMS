# Contains code for displaying images in Google Earth
try:
    from cStringIO import StringIO
except ImportError:
    from StringIO import StringIO
import urllib

from wmsUtils import RequestParser
from wmsExceptions import WMSException
from getmap import _getBbox, _getGoogleEarthFormat

def gearth(req):
    """ Entry point with mod_python """
    doGEarth(req)

def doGEarth(req):
    """ Generates the KML """
    # TODO: can we raise exceptions in KML format?

    # Parse the URL arguments
    params = RequestParser(req.args)
    # We generate the KML in two stages
    stage = params.getParamValue("stage", "1")
    if stage == "1":
        doStageOne(req, params)
    elif stage == "2":
        doStageTwo(req, params)
    else:
        raise WMSException("STAGE must be \"1\" or \"2\"")

def doStageOne(req, params):
    """ Generates the top-level KML containing the network link """

    layers = params.getParamValue("layers").split(",")
    styles = params.getParamValue("styles").split(",")
    # We must either have one style per layer or else an empty parameter: "STYLES="
    if len(styles) != len(layers) and styles != ['']:
        raise WMSException("You must request exactly one STYLE per layer, or use"
           + " the default style for each layer with STYLES=")

    zValue = params.getParamValue("elevation", "")
    if len(zValue.split(",")) > 1 or len(zValue.split("/")) > 1:
        raise WMSException("You may only request a single value of ELEVATION")

    tValue = params.getParamValue("time", "")
    if len(tValue.split(",")) > 1 or len(tValue.split("/")) > 1:
        raise WMSException("You may only request a single value of TIME")

    s = StringIO()
    s.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    s.write("<kml xmlns=\"http://earth.google.com/kml/2.0\">")
    s.write("<Folder>")
    s.write("<NetworkLink>")
    s.write("<description>%s</description>" % "Description")
    s.write("<name>%s</name>" % "Name")
    s.write("<visibility>1</visibility>")
    s.write("<open>0</open>")
    s.write("<refreshVisibility>0</refreshVisibility>")
    s.write("<flyToView>0</flyToView>")

    s.write("<Url>")
    s.write("<href>http://%s%s?STAGE=2&amp;LAYERS=%s&amp;STYLES=%s&amp;ELEVATION=%s&amp;TIME=%s</href>" %
        (req.server.server_hostname, req.unparsed_uri.split("?")[0],
        ",".join(layers), ",".join(styles), zValue, tValue))
    s.write("<refreshInterval>1</refreshInterval>")
    s.write("<viewRefreshMode>onStop</viewRefreshMode>")
    s.write("<viewRefreshTime>0</viewRefreshTime>")
    s.write("</Url>")

    s.write("</NetworkLink>")

    s.write("</Folder>")
    s.write("</kml>")
    req.content_type = _getGoogleEarthFormat()
    req.headers_out["Content-Disposition"] = "inline; filename=%s_%s.kml" % tuple(layers[0].split("/"))
    req.write(s.getvalue())
    s.close()
    return

def doStageTwo(req, params):
    """ Generates the second-level KML containing the link to the image """

    # TODO: repeats code from doStageOne
    layers = params.getParamValue("layers").split(",")
    styles = params.getParamValue("styles").split(",")
    # We must either have one style per layer or else an empty parameter: "STYLES="
    if len(styles) != len(layers) and styles != ['']:
        raise WMSException("You must request exactly one STYLE per layer, or use"
           + " the default style for each layer with STYLES=")

    zValue = params.getParamValue("elevation", "")
    if len(zValue.split(",")) > 1 or len(zValue.split("/")) > 1:
        raise WMSException("You may only request a single value of ELEVATION")

    tValue = params.getParamValue("time", "")
    if len(tValue.split(",")) > 1 or len(tValue.split("/")) > 1:
        raise WMSException("You may only request a single value of TIME")

    # Get the bounding box (this is appended by Google Earth itself)
    bbox = _getBbox(params)

    s = StringIO()
    s.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    s.write("<kml xmlns=\"http://earth.google.com/kml/2.0\">")

    s.write("<Folder>")
    s.write("<visibility>1</visibility>")
    # TODO: do a GroundOverlay for each layer
    s.write("<GroundOverlay>")
    s.write("<description>Description goes here</description>")
    s.write("<name>Ocean data</name>")
    s.write("<visibility>1</visibility>")

    # TODO: get some of these parameters more intelligently
    s.write("<Icon><href>")
    s.write("http://%s/ncWMS/WMS.py?" % req.server.server_hostname)
    s.write("SERVICE=WMS&amp;REQUEST=GetMap&amp;VERSION=1.3.0&amp;CRS=CRS:84&amp;FORMAT=image/png&amp;WIDTH=500&amp;HEIGHT=500")
    s.write("&amp;LAYERS=%s&amp;STYLES=%s&amp;ELEVATION=%s&amp;TIME=%s" %
        (",".join(layers), ",".join(styles), zValue, tValue))
    s.write("&amp;BBOX=%s,%s,%s,%s" % tuple([str(f) for f in bbox]))
    s.write("</href></Icon>")

    s.write("<LatLonBox id=\"1\">")
    s.write("<north>%s</north><south>%s</south><east>%s</east><west>%s</west>" %
        tuple([str(f) for f in bbox]))
    s.write("<rotation>0</rotation>")
    s.write("</LatLonBox>")
    s.write("</GroundOverlay>")
    s.write("</Folder>")

    s.write("</kml>")

    req.content_type = "text/xml"
    req.write(s.getvalue())
    s.close()
    return
