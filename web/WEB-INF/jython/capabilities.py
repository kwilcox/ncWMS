# Code for generating the Capabilities document
try:
    from cStringIO import StringIO
except ImportError:
    from StringIO import StringIO
import time

import wmsUtils
import grids
import getfeatureinfo
import getmap
from wmsExceptions import *

def getCapabilities(req, params, config):
    """ Returns the Capabilities document.
        req = mod_python request object or WMS.FakeModPythonRequest object
        params = wmsUtils.RequestParser object containing the request parameters
        config = ConfigParser object containing configuration info for this WMS """

    version = params.getParamValue("version", "")
    format = params.getParamValue("format", "")
    # TODO: deal with version and format

    lastUpdateTime = config.lastUpdateTimeSeconds

    # Check the UPDATESEQUENCE (used for cache consistency)
    updatesequence = params.getParamValue("updatesequence", "")
    if updatesequence != "":
        try:
            us = wmsUtils.isoStringToSeconds(updatesequence)
            if round(us) == round(lastUpdateTime):
                # Equal to the nearest second
                raise CurrentUpdateSequence(updatesequence)
            elif us > lastUpdateTime:
                raise InvalidUpdateSequence(updatesequence)
        except ValueError:
            # Client didn't supply a valid ISO8601 date
            # According to the spec, InvalidUpdateSequence is not the
            # right error code here so we use a generic exception
            raise WMSException("UPDATESEQUENCE must be a valid ISO8601 date")

    output = StringIO()
    output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    output.write("<WMS_Capabilities version=\"" + wmsUtils.getWMSVersion() + "\"")
    # UpdateSequence is accurate to the nearest second
    output.write(" updateSequence=\"%s\"" % wmsUtils.secondsToISOString(round(lastUpdateTime)))
    output.write(" xmlns=\"http://www.opengis.net/wms\"")
    output.write(" xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
    # The next two lines should be commented out if you wish to load this document
    # in Cadcorp SIS from behind the University of Reading firewall
    output.write(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
    output.write(" xsi:schemaLocation=\"http://www.opengis.net/wms http://schemas.opengis.net/wms/1.3.0/capabilities_1_3_0.xsd\"")
    output.write(">")
    
    output.write("<Service>")
    output.write("<Name>WMS</Name>")
    output.write("<Title>%s</Title>" % config.server.title)
    output.write("<Abstract>%s</Abstract>" % config.server.abstract)
    output.write("<KeywordList>")
    for keyword in config.server.keywords.split(","):
        output.write("<Keyword>%s</Keyword>" % keyword)
    output.write("</KeywordList>")
    output.write("<OnlineResource xlink:type=\"simple\" xlink:href=\"%s\"/>" % config.server.url)

    output.write("<ContactInformation>")
    output.write("<ContactPersonPrimary>")
    output.write("<ContactPerson>%s</ContactPerson>" % config.contact.name)
    output.write("<ContactOrganization>%s</ContactOrganization>" % config.contact.org)
    output.write("</ContactPersonPrimary>")
    output.write("<ContactVoiceTelephone>%s</ContactVoiceTelephone>" % config.contact.tel)
    output.write("<ContactElectronicMailAddress>%s</ContactElectronicMailAddress>" % config.contact.email)
    output.write("</ContactInformation>")

    output.write("<Fees>none</Fees>")
    output.write("<AccessConstraints>none</AccessConstraints>")
    output.write("<LayerLimit>%d</LayerLimit>" % getmap.getLayerLimit())
    output.write("<MaxWidth>%d</MaxWidth>" % config.server.maxImageWidth)
    output.write("<MaxHeight>%d</MaxHeight>" % config.server.maxImageHeight)
    output.write("</Service>")
    
    output.write("<Capability>")
    output.write("<Request>")
    output.write("<GetCapabilities>")
    output.write("<Format>text/xml</Format>")
    # Trailing "?" in the URL confuses World Wind 1.4.0.0
    url = "http://%s%s" % (req.server.server_hostname, req.unparsed_uri.split("?")[0])
    output.write("<DCPType><HTTP><Get><OnlineResource xlink:type=\"simple\" xlink:href=\"" +
        url + "\"/></Get></HTTP></DCPType>")
    output.write("</GetCapabilities>")
    output.write("<GetMap>")
    for format in getmap.getSupportedImageFormats():
        output.write("<Format>%s</Format>" % format)
    output.write("<DCPType><HTTP><Get><OnlineResource xlink:type=\"simple\" xlink:href=\"" +
        url + "\"/></Get></HTTP></DCPType>")
    output.write("</GetMap>")
    if config.server.allowFeatureInfo:
        output.write("<GetFeatureInfo>")
        for format in getfeatureinfo.getSupportedFormats():
            output.write("<Format>%s</Format>" % format)
        output.write("<DCPType><HTTP><Get><OnlineResource xlink:type=\"simple\" xlink:href=\"" +
            url + "\"/></Get></HTTP></DCPType>")
        output.write("</GetFeatureInfo>")
    output.write("</Request>")
    # TODO: support more exception types
    output.write("<Exception>")
    for ex_format in getmap.getSupportedExceptionFormats():
        output.write("<Format>%s</Format>" % ex_format)
    output.write("</Exception>")

    # Write the top-level container layer
    output.write("<Layer>")
    output.write("<Title>%s</Title>" % config.server.title)
    # TODO: add styles
    for crs in grids.getSupportedCRSs().keys():
        output.write("<CRS>" + crs + "</CRS>")
    
    # Now for the dataset layers
    datasets = config.datasets
    for dsid in datasets.keys():
        if datasets[dsid].ready:
            # Write a container layer for this dataset. Container layers
            # do not have a Name
            output.write("<Layer>")
            output.write("<Title>%s</Title>" % datasets[dsid].title)
            # Now write the displayable data layers
            vars = datasets[dsid].variables
            for vid in vars.keys():
                output.write("<Layer")
                if config.server.allowFeatureInfo and datasets[dsid].queryable:
                    output.write(" queryable=\"1\"")
                output.write(">")
                output.write("<Name>%s%s%s</Name>" % (dsid, wmsUtils.getLayerSeparator(), vid))
                output.write("<Title>%s</Title>" % vars[vid].title)
                output.write("<Abstract>%s</Abstract>" % vars[vid].abstract)

                # Set the bounding box
                minLon, minLat, maxLon, maxLat = vars[vid].bbox
                output.write("<EX_GeographicBoundingBox>")
                output.write("<westBoundLongitude>%s</westBoundLongitude>" % str(minLon))
                output.write("<eastBoundLongitude>%s</eastBoundLongitude>" % str(maxLon))
                output.write("<southBoundLatitude>%s</southBoundLatitude>" % str(minLat))
                output.write("<northBoundLatitude>%s</northBoundLatitude>" % str(maxLat))
                output.write("</EX_GeographicBoundingBox>")
                output.write("<BoundingBox CRS=\"CRS:84\" ")
                output.write("minx=\"%f\" maxx=\"%f\" miny=\"%f\" maxy=\"%f\"/>"
                    % (minLon, maxLon, minLat, maxLat))

                # Set the level dimension
                if vars[vid].zvalues is not None:
                    output.write("<Dimension name=\"elevation\" units=\"%s\"" 
                        % vars[vid].zunits)
                    # Use the first value in the array as the default
                    # If the default value is removed, you also need to edit
                    # the data reading code (e.g. DataReader.java) to
                    # disallow default z values
                    output.write(" default=\"%s\">" % vars[vid].zvalues[0])
                    firstTime = 1
                    for z in vars[vid].zvalues:
                        if firstTime:
                            firstTime = 0
                        else:
                            output.write(",")
                        output.write(str(z))
                    output.write("</Dimension>")

                # Set the time dimension
                if len(vars[vid].tvalues) > 0:
                    output.write("<Dimension name=\"time\" units=\"ISO8601\"")
                    # TODO: default value should be the time closest to now
                    output.write(" multipleValues=\"true\" current=\"true\" default=\"%s\">" %
                        wmsUtils.secondsToISOString(vars[vid].tvalues[-1]))
                    firstTime = 1
                    for t in vars[vid].tvalues:
                        if firstTime:
                            firstTime = 0
                        else:
                            output.write(",")
                        output.write(wmsUtils.secondsToISOString(t))
                    output.write("</Dimension>")

                output.write("</Layer>") # end of variable Layer
            output.write("</Layer>") # end of dataset layer
    
    output.write("</Layer>") # end of top-level container layer
    
    output.write("</Capability>")
    output.write("</WMS_Capabilities>")

    req.content_type="text/xml"
    req.write(output.getvalue())
    output.close() # Free the buffer
    return
