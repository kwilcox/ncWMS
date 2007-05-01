# Implements the GetMap operation

import sys

if sys.platform.startswith("java"):
    # We're running on Jython
    import javagraphics as graphics
    from uk.ac.rdg.resc.ncwms.cache import ImageTileKey
else:
    # TODO: check for presence of CDAT
    import graphics
    class ImageTileKey: pass
from wmsExceptions import *
import wmsUtils
import grids

def getLayerLimit():
    """ returns the maximum number of layers that can be requested in GetMap """
    return 1

def getSupportedImageFormats():
    """ returns the image formats supported by this operation """
    return graphics.getSupportedImageFormats()

def getSupportedExceptionFormats():
    """ The exception formats supported by this operation """
    # Supporting other exception formats (e.g. INIMAGE) will take a bit
    # of work in the exception-handling code
    return ["XML"]

def getMap(req, params, config, cache):
    """ The GetMap operation.
       req = mod_python request object (or FakeModPythonRequestObject from Jython servlet)
       params = wmsUtils.RequestParser object containing the request parameters
       config = configuration object
       cache = cache of image tiles """
    
    _checkVersion(params) # Checks the VERSION parameter
    
    layers = params.getParamValue("layers").split(",")
    if len(layers) > getLayerLimit():
        raise WMSException("You may only request a maximum of " +
            str(getLayerLimit()) + " layer(s) simultaneously from this server")

    # Find the source of the requested data
    dataset, varID = _getDatasetAndVariableID(layers, config.datasets)
    # Get the metadata
    var = dataset.variables[varID]
    
    styles = params.getParamValue("styles").split(",")
    # We must either have one style per layer or else an empty parameter: "STYLES="
    if len(styles) != len(layers) and styles != ['']:
            raise WMSException("You must request exactly one STYLE per layer, or use"
           + " the default style for each layer with STYLES=")
    for style in styles:
        if style != "":
            # TODO: handle styles properly
            raise StyleNotDefined(style)
    # Get a Style object that turns data arrays into arrays of pixels:
    # these pixels will be formatted into images using a Format object
    style = graphics.getStyle(styles[0]) 
    
    # RequestParser replaces pluses with spaces: we must change back
    # to parse the format correctly
    format = params.getParamValue("format").replace(" ", "+")
    # Get a picture making object for this MIME type: this will throw
    # an InvalidFormat exception if the format is not supported
    picMaker = graphics.getPicMaker(format)

    exception_format = params.getParamValue("exceptions", "XML")
    if exception_format not in getSupportedExceptionFormats():
        raise InvalidFormat("exception", exception_format, "GetMap")

    zValue = _getZValue(params)

    picMaker.var = var # This is used to create descriptions in the KML

    # Get the requested indices along the time axis
    tIndices = _getTIndices(var, params)

    # Get the requested transparency and background colour for the layer
    trans = params.getParamValue("transparent", "false").lower()
    if trans == "false":
        style.transparent = 0
    elif trans == "true":
        style.transparent = 1
    else:
        raise WMSException("The value of TRANSPARENT must be \"TRUE\" or \"FALSE\"")
    
    bgc = params.getParamValue("bgcolor", "0xFFFFFF")
    if len(bgc) != 8 or not bgc.startswith("0x"):
        raise WMSException("Invalid format for BGCOLOR")
    try:
        style.bgColor = eval(bgc) # Parses hex string into an integer
    except:
        raise WMSException("Invalid format for BGCOLOR")

    # Get the extremes of the colour scale
    # SCALE is now handled as part of the STYLE specification
    #picMaker.scaleMin, picMaker.scaleMax = _getScale(params)

    # Get the percentage opacity of the map layer: another WMS extension
    # Opacity is now handled as part of the STYLE specification
    #opa = params.getParamValue("opacity", "100")
    #try:
    #    picMaker.opacity = int(opa)
    #except:
    #    raise WMSException("The OPACITY parameter must be a valid number in the range 0 to 100 inclusive")

    # Generate a grid of lon,lat points, one for each image pixel
    bbox = _getBbox(params)
    grid = _getGrid(params, bbox, config)
    style.picWidth, style.picHeight = grid.width, grid.height
    style.fillValue = _getFillValue()

    # Read the data for the image frames
    isanimation = len(tIndices) > 1
    for tIndex in tIndices:
        picData = [] # Contains one (scalar) or two (vector) components
        if var.vector:
            picData.append(readPicData(dataset, var.eastwardComponent, params.getParamValue("crs"), layers[0], bbox, grid, zValue, tIndex, cache))
            picData.append(readPicData(dataset, var.northwardComponent, params.getParamValue("crs"), layers[0], bbox, grid, zValue, tIndex, cache))
        else:
            picData.append(readPicData(dataset, var, params.getParamValue("crs"), layers[0], bbox, grid, zValue, tIndex, cache))

        if len(var.tvalues) == 0:
            tValue = ""
        else:
            tValue = wmsUtils.secondsToISOString(var.tvalues[tIndex])
        # TODO: deal with vectors properly
        style.addFrame(picData[0], tValue) # the tValue is the label for the image
    # Write the image to the client
    req.content_type = picMaker.mimeType
    # If this is a KMZ file give it a sensible filename
    if picMaker.mimeType == "application/vnd.google-earth.kmz":
        req.headers_out["Content-Disposition"] = "inline; filename=%s_%s.kmz" % (dataset.id, varID)
    picMaker.renderedFrames = style.renderedFrames
    graphics.writePicture(req, picMaker)

    return

def _checkVersion(params):
    """ Checks that the VERSION parameter exists and is correct """
    version = params.getParamValue("version")
    if version != wmsUtils.getWMSVersion():
        raise WMSException("VERSION must be %s" % wmsUtils.getWMSVersion())

def _getBbox(params):
    """ Gets the bounding box as a list of four floating-point numbers """
    bboxEls = params.getParamValue("bbox").split(",")
    if len(bboxEls) != 4:
        raise WMSException("Invalid bounding box format: need four elements")
    try:
        bbox = [float(el) for el in bboxEls]
    except ValueError:
        raise WMSException("Invalid bounding box format: all elements must be numeric")
    if bbox[0] >= bbox[2] or bbox[1] >= bbox[3]:
        raise WMSException("Invalid bounding box format")
    return bbox

def _getGrid(params, bbox, config):
    """ Gets the grid for the map """

    # Get the image width and height
    try:
        width = int(params.getParamValue("width"))
        height = int(params.getParamValue("height"))
        if width < 1 or width > config.server.maxImageWidth:
            raise WMSException("Image width must be between 1 and " +
                str(config.server.maxImageWidth) + " pixels inclusive")
        if height < 1 or height > config.server.maxImageHeight:
            raise WMSException("Image height must be between 1 and " +
                str(config.server.maxImageHeight) + " pixels inclusive")
    except ValueError:
        raise WMSException("Invalid integer provided for WIDTH or HEIGHT")

    # Get the Grid object
    crs = params.getParamValue("crs")
    if grids.getSupportedCRSs().has_key(crs):
        GridClass = grids.getSupportedCRSs()[crs] # see grids.py
        return GridClass(bbox, width, height)
    else:
        raise InvalidCRS(crs)

def _getScale(params):
    # Get the scale for colouring the map: this is an extension to the
    # WMS specification
    scale = params.getParamValue("scale", "0,0") # 0,0 signals auto-scale
    if len(scale.split(",")) == 2:
        try:
            scaleMin, scaleMax = [float(x) for x in scale.split(",")]
            if (scaleMin != 0 or scaleMax != 0) and scaleMin >= scaleMax:
                raise WMSException("SCALE min value must be less than max value")
            return scaleMin, scaleMax
        except ValueError:
            raise WMSException("Invalid number in SCALE parameter")
    else:     
        raise WMSException("The SCALE parameter must be of the form SCALEMIN,SCALEMAX")

def _getDatasetAndVariableID(layers, datasets):
    """ Returns a (dataset, varID) tuple containing the dataset and
        the ID of the variable 
        Only deals with one layer at the moment """
    dsAndVar = layers[0].split(wmsUtils.getLayerSeparator())
    try:
        return datasets[dsAndVar[0]], dsAndVar[1]
    except KeyError:
        raise LayerNotDefined(layers[0])

def _getZValue(params):
    """ Gets the value of ELEVATION """
    zValue = params.getParamValue("elevation", "")
    if len(zValue.split(",")) > 1 or len(zValue.split("/")) > 1:
        raise InvalidDimensionValue("elevation", "You may only request a single value")
    return zValue

def _getTIndices(var, params):
    """ Find the requested index/indices along the time axis.
        Returns a list of indices """
    tvals = var.tvalues
    if len(tvals) == 0:
        # Ignore any time value that was given by the client (TODO OK?)
        tIndices = [0] # This layer has no time dimension
    else:
        # The time axis exists
        reqtime = params.getParamValue("time", "")
        if reqtime == "":
            # The default time is the last value along the axis
            # TODO: this should be the time closest to now
            tIndices = [len(tvals) - 1]
        else:
            # Interpret the time specification
            tIndices = []
            for tSpec in reqtime.split(","):
                startStopPeriod = tSpec.split("/")
                if len(startStopPeriod) == 1:
                    # This is a single time value
                    tIndex = var.findTIndex(startStopPeriod[0])
                    tIndices.append(tIndex)
                elif len(startStopPeriod) == 2:
                    # Extract all time values from start to stop inclusive
                    start, stop = startStopPeriod
                    startIndex = var.findTIndex(startStopPeriod[0])
                    stopIndex = var.findTIndex(startStopPeriod[1])
                    for i in xrange(startIndex, stopIndex + 1):
                        tIndices.append(i)
                elif len(startStopPeriod) == 3:
                    # Extract time values from start to stop inclusive
                    # with a set periodicity
                    start, stop, period = startStopPeriod
                    raise WMSException("Cannot yet handle animations with a set periodicity")
                else:
                    raise InvalidDimensionValue("time", tSpec)
    return tIndices

def _getFillValue():
    """ returns the fill value to be used internally - can't be NaN because NaN is 
        not portable across Python versions or Jython """
    return 1.0e20

def readPicData(dataset, var, crs, layer, bbox, grid, zValue, tIndex, cache):
    """ Reads the data for the given variable"""

    picData = None

    if cache != None:
        # See if we already have this data array in cache
        # Create the key for this data array
        key = ImageTileKey()
        key.crs = crs
        key.layer = layer
        key.bbox = bbox
        key.width, key.height = grid.width, grid.height
        if zValue.strip() == "":
            key.elevation = ""
        else:
            key.elevation = str(float(zValue)) # removes trailing zeros
        if len(var.tvalues) == 0:
            key.time = 0.0
        else:
            key.time = var.tvalues[tIndex]
        picData = cache.getImageTile(key)

    if picData is None:
        # TODO: deal with non lat-lon grids
        picData = dataset.read(var, tIndex, zValue, grid.latValues, grid.lonValues, _getFillValue())
        if cache != None:
            cache.putImageTile(key, picData)

    return picData
        
