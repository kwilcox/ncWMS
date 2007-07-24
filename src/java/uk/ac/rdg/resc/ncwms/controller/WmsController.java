/*
 * Copyright (c) 2007 The University of Reading
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package uk.ac.rdg.resc.ncwms.controller;

import com.sun.org.apache.xpath.internal.operations.Variable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import uk.ac.rdg.resc.ncwms.config.Config;
import uk.ac.rdg.resc.ncwms.grids.AbstractGrid;
import uk.ac.rdg.resc.ncwms.datareader.VariableMetadata;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidCrsException;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidFormatException;
import uk.ac.rdg.resc.ncwms.exceptions.OperationNotSupportedException;
import uk.ac.rdg.resc.ncwms.exceptions.StyleNotDefinedException;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;
import uk.ac.rdg.resc.ncwms.graphics.PicMaker;
import uk.ac.rdg.resc.ncwms.styles.AbstractStyle;
import uk.ac.rdg.resc.ncwms.utils.WmsUtils;

/**
 * Entry point for the WMS.  Note that we cannot use a CommandController here
 * because there is no (apparent) way in Spring to use case-insensitive parameter
 * names to bind request parameters to an object.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class WmsController extends AbstractController
{
    private static final Logger logger = Logger.getLogger(WmsController.class);
    
    /**
     * The maximum number of layers that can be requested in a single GetMap
     * operation
     */
    static final int LAYER_LIMIT = 1;
    /**
     * The fill value to use when reading data and making pictures
     */
    private static final float FILL_VALUE = Float.NaN;
    
    // These objects will be injected by Spring
    private Config config;
    private Factory<PicMaker> picMakerFactory;
    private Factory<AbstractStyle> styleFactory;
    private Factory<AbstractGrid> gridFactory;
    private MetadataController metadataController;
    
    /**
     * Entry point for all requests to the WMS
     */
    protected ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse) throws Exception
    {
        // Create an object that allows request parameters to be retrieved in
        // a way that is not sensitive to the case of the parameter NAMES
        // (but is sensitive to the case of the parameter VALUES).
        RequestParams params = new RequestParams(httpServletRequest.getParameterMap());
        
        // Check the REQUEST parameter to see if we're producing a capabilities
        // document, a map or a FeatureInfo
        String request = params.getMandatoryString("request");
        if (request.equals("GetCapabilities"))
        {
            return getCapabilities(httpServletRequest, params);
        }
        else if (request.equals("GetMap"))
        {
            return getMap(params);
        }
        else if (request.equals("GetFeatureInfo"))
        {
            // TODO
        }
        else if (request.equals("GetMetadata"))
        {
            // This is a request for non-standard metadata.  (This will one
            // day be replaced by queries to Capabilities fragments, if possible.)
            // Delegate to the MetadataController
            return this.metadataController.handleRequest(httpServletRequest,
                httpServletResponse);
        }
        else
        {
            throw new OperationNotSupportedException(request);
        }
        
        return null;
    }
    
    /**
     * Executes the GetCapabilities operation, returning a ModelAndView for 
     * display of the information.
     * @todo allow the display of certain layers, or groups of layers.
     */
    private ModelAndView getCapabilities(HttpServletRequest httpServletRequest,
        RequestParams params) throws WmsException
    {
        // Check the SERVICE parameter
        String service = params.getMandatoryString("service");
        if (!service.equals("WMS"))
        {
            throw new WmsException("The value of the SERVICE parameter must be \"WMS\"");
        }
        
        // Check the VERSION parameter
        String version = params.getString("version");
        // We do nothing else here because we only support one version
        
        // Check the FORMAT parameter
        String format = params.getString("format");
        // The WMS 1.3.0 spec says that we can respond with the default text/xml
        // format if the client has requested an unknown format.  Hence we do
        // nothing here.
        
        // TODO: check the UPDATESEQUENCE parameter
        
        Map<String, Object> models = new HashMap<String, Object>();
        models.put("config", this.config);
        models.put("wmsBaseUrl", httpServletRequest.getRequestURL().toString());
        models.put("supportedImageFormats", this.picMakerFactory.getKeys());
        models.put("layerLimit", LAYER_LIMIT);
        return new ModelAndView("capabilities_xml", models);
    }
    
    /**
     * Executes the GetMap operation
     * @throws WmsException if the user has provided invalid parameters
     * @throws Exception if an internal error occurs
     */
    public ModelAndView getMap(RequestParams params) throws WmsException, Exception
    {
        String version = params.getMandatoryString("version");
        if (!version.equals(WmsUtils.VERSION))
        {
            throw new WmsException("VERSION must be " + WmsUtils.VERSION);
        }
        
        String[] layers = params.getMandatoryString("layers").split(",");
        if (layers.length > LAYER_LIMIT)
        {
            throw new WmsException("You may only request a maximum of " +
                LAYER_LIMIT + " layer(s) simultaneously from this server");
        }
        
        // RequestParser replaces pluses with spaces: we must change back
        // to parse the format correctly
        String mimeType = params.getMandatoryString("format").replaceAll(" ", "+");
        // Get the PicMaker that corresponds with this MIME type
        PicMaker picMaker = this.picMakerFactory.createObject(mimeType);
        if (picMaker == null)
        {
            throw new InvalidFormatException("The image format " + mimeType + 
                " is not supported by this server");
        }
        picMaker.setMimeType(mimeType); // Some PicMakers support multiple mime types
        
        // TODO: support more than one layer
        VariableMetadata var = this.config.getVariable(layers[0]);
        AbstractGrid grid = this.getGrid(params);
        AbstractStyle style = this.getStyle(params, layers, var, grid);
        // TODO: deal with EXCEPTIONS
        int zIndex = getZIndex(params, var); // -1 if no z axis present
        
        // Cycle through all the provided timesteps, extracting data for each step
        // If there is no time axis getTIndices will return a single value of -1
        for (int tIndex : getTIndices(params, var))
        {
            
        }

        return null;
    }
    
    /**
     * Gets the style object that will be used to control the rendering of the
     * image.  Sets the transparency and background colour.
     * @todo support returning of multiple styles
     */
    private AbstractStyle getStyle(RequestParams params, String[] layers,
        VariableMetadata var, AbstractGrid grid) throws Exception
    {
        String stylesStr = params.getMandatoryString("styles");
        // The split will result in an array of at least one String even if
        // stylesStr is the empty string
        String[] styles = stylesStr.split(",");
        if (styles.length != layers.length && !stylesStr.equals(""))
        {
            throw new WmsException("You must request exactly one STYLE per layer, or use"
               + " the default style for each layer with STYLES=");
        }
        AbstractStyle style = null;
        if (styles[0].equals(""))
        {
            // Use the default style for the variable
            style = this.styleFactory.createObject(var.getDefaultStyleKey());
            assert style != null;
        }
        else
        {
            // Get the full Style object (with attributes set)
            String[] els = styles[0].split(";");
            style = this.styleFactory.createObject(els[0]);
            if (style == null)
            {
                throw new StyleNotDefinedException(style + " is not a valid STYLE");
            }
            if (!var.supportsStyle(els[0]))
            {
                throw new StyleNotDefinedException("The layer " + layers[0] +
                    " does not support the style \"" + els[0] + "\"");
            }
            // Set the attributes of the AbstractStyle
            for (int i = 1; i < els.length; i++)
            {
                String[] keyAndValues = els[i].split(":");
                if (keyAndValues.length < 2)
                {
                    throw new StyleNotDefinedException("STYLE specification format error");
                }
                // Get the array of values for this attribute
                String[] vals = new String[keyAndValues.length - 1];
                System.arraycopy(keyAndValues, 1, vals, 0, vals.length);
                style.setAttribute(keyAndValues[0], vals);
            }
            logger.debug("Style object of type {} created from style spec {}",
                style.getClass(), styles[0]);
        }
        style.setFillValue(FILL_VALUE);
                
        // Get the requested transparency and background colour for the layer
        String trans = params.getString("transparent", "false").toLowerCase();
        if (trans.equals("false")) style.setTransparent(false);
        else if (trans.equals("true")) style.setTransparent(true);
        else throw new WmsException("The value of TRANSPARENT must be \"TRUE\" or \"FALSE\"");

        String bgc = params.getString("bgcolor", "0xFFFFFF");
        if (bgc.length() != 8 || !bgc.startsWith("0x"))
        {
            throw new WmsException("Invalid format for BGCOLOR");
        }
        try
        {
            style.setBgColor(Integer.parseInt(bgc.substring(2), 16));
        }
        catch(NumberFormatException nfe)
        {
            throw new WmsException("Invalid format for BGCOLOR");
        }
        
        style.setPicWidth(grid.getWidth());
        style.setPicHeight(grid.getHeight());
        
        return style;
    }
    
    /**
     * Gets the grid for the image from the request parameters
     * @throws WmsException if there is an error in the parameters
     * @throws Exception if there was an unexpected internal error
     */
    private AbstractGrid getGrid(RequestParams params)
        throws WmsException, Exception
    {
        // Get the bounding box first
        String[] bboxEls = params.getMandatoryString("bbox").split(",");
        if (bboxEls.length != 4)
        {
            throw new WmsException("Invalid bounding box format: need four elements");
        }
        float[] bbox = new float[4];
        try
        {
            for (int i = 0; i < bbox.length; i++)
            {
                bbox[i] = Float.parseFloat(bboxEls[i]);
            }
        }
        catch(NumberFormatException nfe)
        {
            throw new WmsException("Invalid bounding box format: all elements must be numeric");
        }
        if (bbox[0] >= bbox[2] || bbox[1] >= bbox[3])
        {
            throw new WmsException("Invalid bounding box format");
        }
        
        // Set up the grid onto which the data will be projected
        String crsCode = params.getMandatoryString("CRS");
        AbstractGrid grid = this.gridFactory.createObject(crsCode);
        if (grid == null)
        {
            throw new InvalidCrsException(crsCode);
        }
        grid.setWidth(params.getMandatoryInt("WIDTH"));
        grid.setHeight(params.getMandatoryInt("HEIGHT"));
        grid.setBbox(bbox);
        
        return grid;
    }
    
    /**
     * @return the index on the z axis of the requested Z value.  Returns 0 (the
     * default) if no value has been specified and the provided Variable has a z
     * axis.  Returns -1 if no value has been specified and none is needed.
     * @throws InvalidDimensionValueException if the provided z value is not
     * a valid floating-point number or if it is not a valid value for this axis.
     */
    private static int getZIndex(RequestParams params, VariableMetadata var)
        throws InvalidDimensionValueException
    {
        // Get the z value.  The default value is the first value in the array
        // of z values
        String zStr = params.getString("elevation");
        if (zStr == null)
        {
            // No value has been specified
            return var.isZaxisPresent() ? var.getDefaultZIndex() : -1;
        }
        // The user has specified a z value.  Check that we have a z axis
        if (!var.isZaxisPresent())
        {
            throw new InvalidDimensionValueException("elevation", zStr);
        }
        // Check to see if this is a single value (the
        // user hasn't requested anything of the form z1,z2 or start/stop/step)
        if (zStr.split(",").length > 1 || zStr.split("/").length > 1)
        {
            throw new InvalidDimensionValueException("elevation", zStr);
        }
        try
        {
            return var.findZIndex(zStr);
        }
        catch(NumberFormatException nfe)
        {
            throw new InvalidDimensionValueException("elevation", zStr);
        }
    }
    
    /**
     * @return a List of indices along the time axis corresponding with the
     * requested TIME parameter.  If there is no time axis, this will return
     * a List with a single value of -1.
     */
    private static List<Integer> getTIndices(RequestParams params, VariableMetadata var)
        throws WmsException
    {
        List<Integer> tIndices = new ArrayList<Integer>();
        String timeSpec = params.getString("time");
        if (var.isTaxisPresent())
        {
            if (timeSpec == null)
            {
                // The default time is the last value along the axis
                // TODO: this should be the time closest to now
                tIndices.add(var.getLastTIndex());
            }
            else
            {
                // Interpret the time specification
                for (String t : timeSpec.split(","))
                {
                    String[] startStopPeriod = t.split("/");
                    if (startStopPeriod.length == 1)
                    {
                        // This is a single time value
                        tIndices.add(var.findTIndex(startStopPeriod[0]));
                    }
                    else if (startStopPeriod.length == 2)
                    {
                        // Extract all time values from start to stop inclusive
                        int startIndex = var.findTIndex(startStopPeriod[0]);
                        int stopIndex = var.findTIndex(startStopPeriod[1]);
                        for (int i = startIndex; i <= stopIndex; i++)
                        {
                            tIndices.add(i);
                        }
                    }
                    else if (startStopPeriod.length == 3)
                    {
                        // Extract time values from start to stop inclusive
                        // with a set periodicity
                        throw new WmsException("Cannot yet handle animations with a set periodicity");
                    }
                    else
                    {
                        throw new InvalidDimensionValueException("time", t);
                    }
                }
            }
        }
        else
        {
            // The variable has no time axis
            if (timeSpec != null)
            {
                throw new InvalidDimensionValueException("time", timeSpec);
            }
            tIndices.add(-1); // Signifies a single frame with no particular time value
        }
        return tIndices;
    }

    /**
     * Called by Spring to inject the configuration object
     */
    public void setConfig(Config config)
    {
        this.config = config;
    }
    
    /**
     * Called by Spring to inject the PicMakerFactory object
     */
    public void setPicMakerFactory(Factory<PicMaker> picMakerFactory)
    {
        this.picMakerFactory = picMakerFactory;
    }
    
    /**
     * Called by Spring to inject the StyleFactory object
     */
    public void setStyleFactory(Factory<AbstractStyle> styleFactory)
    {
        this.styleFactory = styleFactory;
    }
    
    /**
     * Called by Spring to inject the gridFactory object
     */
    public void setGridFactory(Factory<AbstractGrid> gridFactory)
    {
        this.gridFactory = gridFactory;
    }
    
    /**
     * Called by Spring to inject the metadata controller
     */
    public void setMetadataController(MetadataController metadataController)
    {
        this.metadataController = metadataController;
    }
    
    public static void main(String[] args)
    {
        System.out.println("".split(",").length + "");
    }
    
}
