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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import uk.ac.rdg.resc.ncwms.config.Config;
import uk.ac.rdg.resc.ncwms.grids.AbstractGrid;
import uk.ac.rdg.resc.ncwms.datareader.VariableMetadata;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidCrsException;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidFormatException;
import uk.ac.rdg.resc.ncwms.exceptions.LayerNotQueryableException;
import uk.ac.rdg.resc.ncwms.exceptions.OperationNotSupportedException;
import uk.ac.rdg.resc.ncwms.exceptions.StyleNotDefinedException;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;
import uk.ac.rdg.resc.ncwms.graphics.KmzMaker;
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
    private static final int LAYER_LIMIT = 1;
    
    private static final String FEATURE_INFO_XML_FORMAT = "text/xml";
    private static final String FEATURE_INFO_PNG_FORMAT = "image/png";
    
    // These objects will be injected by Spring
    private Config config;
    private Factory<PicMaker> picMakerFactory;
    private Factory<AbstractStyle> styleFactory;
    private Factory<AbstractGrid> gridFactory;
    
    // This will be created when this object is created
    private MetadataController metadataController;
    
    /**
     * Entry point for all requests to the WMS
     */
    protected ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse) throws Exception
    {
        try
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
                return getMap(params, httpServletResponse);
            }
            else if (request.equals("GetFeatureInfo"))
            {
                return getFeatureInfo(params, httpServletResponse);
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
        }
        catch(WmsException wmse)
        {
            // We don't log these errors
            throw wmse;
        }
        catch(Exception e)
        {
            logger.error(e.getMessage(), e);
            throw e;
        }
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
     * @todo Separate Model and View code more cleanly
     */
    public ModelAndView getMap(RequestParams params, HttpServletResponse response)
        throws WmsException, Exception
    {
        GetMapRequest getMapRequest = new GetMapRequest(params);
        
        // Get the PicMaker that corresponds with this MIME type
        String mimeType = getMapRequest.getStyleRequest().getImageFormat();
        PicMaker picMaker = this.picMakerFactory.createObject(mimeType);
        if (picMaker == null)
        {
            throw new InvalidFormatException("The image format " + mimeType + 
                " is not supported by this server");
        }
        
        String[] layers = getMapRequest.getDataRequest().getLayers();
        if (layers.length > LAYER_LIMIT)
        {
            throw new WmsException("You may only request a maximum of " +
                WmsController.LAYER_LIMIT + " layer(s) simultaneously from this server");
        }
        // TODO: support more than one layer
        VariableMetadata var = this.config.getVariable(layers[0]);
        
        // Get the grid onto which the data will be projected
        AbstractGrid grid = getGrid(getMapRequest.getDataRequest(),
            this.gridFactory);
        
        AbstractStyle style = this.getStyle(getMapRequest, var);
        
        String zValue = getMapRequest.getDataRequest().getElevationString();
        int zIndex = getZIndex(zValue, var); // -1 if no z axis present
        
        // Cycle through all the provided timesteps, extracting data for each step
        // If there is no time axis getTimesteps will return a single value of null
        List<String> tValues = new ArrayList<String>();
        String timeString = getMapRequest.getDataRequest().getTimeString();
        List<Integer> tIndices = getTIndices(timeString, var);
        for (int tIndex : tIndices)
        {
            // tIndex == -1 if there is no t axis present
            List<float[]> picData = readData(var, tIndex, zIndex, grid);
            // Only add a label if this is part of an animation
            String tValue = "";
            if (var.isTaxisPresent() && tIndices.size() > 1)
            {
                tValue = WmsUtils.dateToISO8601(var.getTimesteps().get(tIndex).getDate());
            }
            tValues.add(tValue);
            style.addFrame(picData, tValue); // the tValue is the label for the image
        }

        // We write some of the request elements to the picMaker - this is
        // used to create labels and metadata, e.g. in KMZ.
        picMaker.setVar(var);
        picMaker.setTvalues(tValues);
        picMaker.setZvalue(zValue);
        picMaker.setBbox(grid.getBbox());
        // Set the legend if we need one (required for KMZ files, for instance)
        if (picMaker.needsLegend()) picMaker.setLegend(style.getLegend(var));
        
        // Write the image to the client
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(mimeType);
        // If this is a KMZ file give it a sensible filename
        if (picMaker instanceof KmzMaker)
        {
            response.setHeader("Content-Disposition", "inline; filename=" +
                var.getDataset().getId() + "_" + var.getId() + ".kmz");
        }

        // Send the images to the picMaker and write to the output
        // TODO: for KMZ output, better to do this via a JSP page?
        picMaker.writeImage(style.getRenderedFrames(), mimeType,
            response.getOutputStream());

        return null;
    }
    
    /**
     * Reads data from the given variable, returning a List of data arrays.
     * This List will have a single element if the variable is scalar, or two
     * elements if the variable is a vector
     */
    static List<float[]> readData(VariableMetadata var, int tIndex, int zIndex,
        AbstractGrid grid) throws Exception
    {
        List<float[]> picData = new ArrayList<float[]>();
        if (var.isVector())
        {
            picData.add(var.getEastwardComponent().read(tIndex, zIndex, grid));
            picData.add(var.getNorthwardComponent().read(tIndex, zIndex, grid));
        }
        else
        {
            picData.add(var.read(tIndex, zIndex, grid));
        }
        return picData;
    }
    
    /**
     * Executes the GetFeatureInfo operation
     * @throws WmsException if the user has provided invalid parameters
     * @throws Exception if an internal error occurs
     * @todo Separate Model and View code more cleanly
     */
    public ModelAndView getFeatureInfo(RequestParams params, HttpServletResponse response)
        throws WmsException, Exception
    {
        GetFeatureInfoRequest request = new GetFeatureInfoRequest(params);
        GetFeatureInfoDataRequest dataRequest = request.getDataRequest();
        
        // Check the feature count
        if (dataRequest.getFeatureCount() != 1)
        {
            throw new WmsException("Can only provide feature info for one layer at a time");
        }
        
        // Check the output format
        if (!request.getOutputFormat().equals(FEATURE_INFO_XML_FORMAT) &&
            !request.getOutputFormat().equals(FEATURE_INFO_PNG_FORMAT))
        {
            throw new InvalidFormatException("The output format " +
                request.getOutputFormat() + " is not valid for GetFeatureInfo");
        }
        
        // Get the variable we're interested in
        String layer = dataRequest.getLayers()[0];
        VariableMetadata var = this.config.getVariable(layer);
        if (!var.isQueryable())
        {
            throw new LayerNotQueryableException(layer);
        }
        
        // Get the grid onto which the data is being projected
        AbstractGrid grid = getGrid(dataRequest, this.gridFactory);
        // Get the lat and lon of the point of interest
        float lon = grid.getLongitude(dataRequest.getPixelColumn(), dataRequest.getPixelRow());
        float lat = grid.getLatitude(dataRequest.getPixelColumn(), dataRequest.getPixelRow());
        
        // Get the index along the z axis
        int zIndex = getZIndex(dataRequest.getElevationString(), var); // -1 if no z axis present
        
        // Get the information about the requested timesteps
        List<Integer> tIndices = getTIndices(dataRequest.getTimeString(), var);
        
        // Now read the data, mapping date-times to data values
        Map<Date, Float> featureData = new HashMap<Date, Float>();
        for (int tIndex : tIndices)
        {
            Date date = tIndex < 0 ? null : var.getTimesteps().get(tIndex).getDate();
            if (var.isVector())
            {
                float x = var.getEastwardComponent().read(tIndex, zIndex,
                    new float[]{lat}, new float[]{lon})[0];
                float y = var.getNorthwardComponent().read(tIndex, zIndex,
                    new float[]{lat}, new float[]{lon})[0];
                featureData.put(date, (float)Math.sqrt(x * x + y * y));
            }
            else
            {
                float val = var.read(tIndex, zIndex, new float[]{lat},
                    new float[]{lon})[0];
                featureData.put(date, val);
            }
        }
        
        if (request.getOutputFormat().equals(FEATURE_INFO_XML_FORMAT))
        {
            Map<String, Object> models = new HashMap<String, Object>();
            models.put("longitude", lon);
            models.put("latitude", lat);
            models.put("data", featureData);
            return new ModelAndView("showFeatureInfo_xml", models);
        }
        else
        {
            // Must be PNG format: prepare and output the JFreeChart
            // TODO: this is nasty: we're mixing presentation code in the controller
            TimeSeries ts = new TimeSeries("Data", Millisecond.class);
            for (Date date : featureData.keySet())
            {
                ts.add(new Millisecond(date), featureData.get(date));
            }
            TimeSeriesCollection xydataset = new TimeSeriesCollection();
            xydataset.addSeries(ts);

            // Create a chart with no legend, tooltips or URLs
            String title = "Lon: " + lon + ", Lat: " + lat;
            String yLabel = var.getTitle() + "(" + var.getUnits() + ")";
            JFreeChart chart = ChartFactory.createTimeSeriesChart(title,
                "Date / time", yLabel, xydataset, false, false, false);
            response.setContentType("image/png");
            ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, 400, 300);
            return null;
        }
    }
    
    /**
     * Gets the style object that will be used to control the rendering of the
     * image.  Sets the transparency and background colour.
     * @todo support returning of multiple styles
     */
    private AbstractStyle getStyle(GetMapRequest getMapRequest,
        VariableMetadata var) throws StyleNotDefinedException, Exception
    {
        AbstractStyle style = null;
        String[] styleSpecs = getMapRequest.getStyleRequest().getStyles();
        if (styleSpecs.length == 0)
        {
            // Use the default style for the variable
            style = this.styleFactory.createObject(var.getDefaultStyleKey());
            assert style != null;
        }
        else
        {
            // Get the full Style object (with attributes set)
            String[] els = styleSpecs[0].split(";");
            style = this.styleFactory.createObject(els[0]);
            if (style == null)
            {
                throw new StyleNotDefinedException(style + " is not a valid STYLE");
            }
            if (!var.supportsStyle(els[0]))
            {
                throw new StyleNotDefinedException("The style \"" + els[0] +
                    "\" is not supported by this layer");
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
                style.getClass(), styleSpecs[0]);
        }
        style.setTransparent(getMapRequest.getStyleRequest().isTransparent());
        style.setBgColor(getMapRequest.getStyleRequest().getBackgroundColour());
        style.setPicWidth(getMapRequest.getDataRequest().getWidth());
        style.setPicHeight(getMapRequest.getDataRequest().getHeight());
        return style;
    }
    
    /**
     * Gets the grid for the image from the request parameters
     * @throws InvalidCrsException if the provided CRS code is not supported
     * @throws Exception if there was an unexpected internal error
     */
    static AbstractGrid getGrid(GetMapDataRequest dataRequest,
        Factory<AbstractGrid> gridFactory)
        throws InvalidCrsException, Exception
    {
        // Set up the grid onto which the data will be projected
        String crsCode = dataRequest.getCrs();
        AbstractGrid grid = gridFactory.createObject(crsCode);
        if (grid == null)
        {
            throw new InvalidCrsException(crsCode);
        }
        grid.setWidth(dataRequest.getWidth());
        grid.setHeight(dataRequest.getHeight());
        grid.setBbox(dataRequest.getBbox());
        return grid;
    }
    
    /**
     * @return the index on the z axis of the requested Z value.  Returns 0 (the
     * default) if no value has been specified and the provided Variable has a z
     * axis.  Returns -1 if no value is needed because there is no z axis in the
     * data.
     * @throws InvalidDimensionValueException if the provided z value is not
     * a valid floating-point number or if it is not a valid value for this axis.
     */
    static int getZIndex(String zValue, VariableMetadata var)
        throws InvalidDimensionValueException
    {
        // Get the z value.  The default value is the first value in the array
        // of z values
        if (zValue == null)
        {
            // No value has been specified
            return var.isZaxisPresent() ? var.getDefaultZIndex() : -1;
        }
        // The user has specified a z value.  Check that we have a z axis
        if (!var.isZaxisPresent())
        {
            return -1; // We ignore the given value
        }
        // Check to see if this is a single value (the
        // user hasn't requested anything of the form z1,z2 or start/stop/step)
        if (zValue.split(",").length > 1 || zValue.split("/").length > 1)
        {
            throw new InvalidDimensionValueException("elevation", zValue);
        }
        return var.findZIndex(zValue);
    }
    
    /**
     * @return a List of indices along the time axis corresponding with the
     * requested TIME parameter.  If there is no time axis, this will return
     * a List with a single value of -1.
     */
    static List<Integer> getTIndices(String timeString, VariableMetadata var)
        throws WmsException
    {
        List<Integer> tIndices = new ArrayList<Integer>();
        if (var.isTaxisPresent())
        {
            if (timeString == null)
            {
                // The default time is the last value along the axis
                // TODO: this should be the time closest to now
                tIndices.add(var.getDefaultTIndex());
            }
            else
            {
                // Interpret the time specification
                for (String t : timeString.split(","))
                {
                    String[] startStopPeriod = t.split("/");
                    if (startStopPeriod.length == 1)
                    {
                        // This is a single time value
                        tIndices.add(var.findTIndex(startStopPeriod[0]));
                    }
                    else if (startStopPeriod.length == 2)
                    {
                        // Use all time values from start to stop inclusive
                        tIndices.addAll(var.findTIndices(startStopPeriod[0],
                            startStopPeriod[1]));
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
            // The variable has no time axis.  We ignore any provided TIME value.
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
    
}
