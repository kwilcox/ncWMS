# Implements the GetFeatureInfo operation.
# WMS1.3.0 spec is a little ambiguous regarding this operation.
# The spec implies that we need all of the GetMap parameters except
# VERSION and REQUEST.  However, the spec also defines QUERY_LAYERS,
# which seems to duplicate LAYERS from GetMap.  I can see no use
# for GetMap's STYLES, FORMAT, TRANSPARENT or BGCOLOR parameters here.

import sys
if sys.platform.startswith("java"):
    # We're running on Jython
    import nj22dataset as datareader
else:
    # TODO: check for presence of CDAT
    import cdmsdataset as datareader
import config
from wmsExceptions import *
from getmap import _getGrid, _checkVersion, _getLocationAndVariableID

def getFeatureInfo(req, params, datasets):
    """ The GetFeatureInfo operation.
       req = mod_python request object (or FakeModPythonRequestObject from Jython servlet)
       params = ncWMS.RequestParser object containing the request parameters
       datasets = dictionary of ncWMS.Datasets, indexed by unique id """
    
    _checkVersion(params)
    grid = _getGrid(params)

    query_layers = params.getParamValue("query_layers").split(",")
    if len(query_layers) > 1:
        raise WMSException("You may only perform GetFeatureInfo on a single layer")

    info_format = params.getParamValue("info_format")
    if info_format not in config.FEATURE_INFO_FORMATS:
        raise InvalidFormat("info", info_format, "GetFeatureInfo")

    exception_format = params.getParamValue("exceptions", "XML")
    if exception_format != "XML":
        raise InvalidFormat("exception", exception_format, "GetFeatureInfo")

    zValue = params.getParamValue("elevation", "")
    if len(zValue.split(",")) > 1 or len(zValue.split("/")) > 1:
        raise WMSException("You may only request a single value of ELEVATION")

    tValue = params.getParamValue("time", "")
    if len(tValue.split(",")) > 1 or len(tValue.split("/")) > 1:
        raise WMSException("You may only request a single value of TIME")

    # Get the i and j coordinate of the feature in pixels
    try:
        i = int(params.getParamValue("i"))
        j = int(params.getParamValue("j"))
        if i < 0 or i >= grid.width:
            raise InvalidPoint(i)
        if j < 0 or j >= grid.height:
            raise InvalidPoint(j)
    except ValueError:
        raise InvalidPoint()

    feature_count = params.getParamValue("feature_count", "1")
    try:
        if int(feature_count) != 1:
            raise WMSException("Can only provide FeatureInfo for 1 feature per layer")
    except ValueError:
        raise WMSException("Invalid integer for FEATURE_COUNT")

    # Get the longitude and latitude of the data point
    lon, lat = grid.getLonLat(i, j)

    # Read the data point
    location, varID, queryable = _getLocationAndVariableID(query_layers, datasets)
    if not queryable:
        raise LayerNotQueryable(query_layers[0])
    value = datareader.readDataValue(location, varID, tValue, zValue, lat, lon)

    # Output in simple XML
    req.content_type = "text/xml"
    req.write(XML_HEADER)
    req.write("<FeatureInfoResponse>")
    req.write("<longitude>%f</longitude>" % lon)
    req.write("<latitude>%f</latitude>" % lat)
    if value is None:
        req.write("<value>none</value>") 
    else:
        req.write("<value>%s</value>" % str(value))
    req.write("</FeatureInfoResponse>")

    return