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

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.imageio.ImageIO;
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
import ucar.unidata.geoloc.LatLonPoint;
import uk.ac.rdg.resc.ncwms.cache.TileCache;
import uk.ac.rdg.resc.ncwms.cache.TileCacheKey;
import uk.ac.rdg.resc.ncwms.config.Config;
import uk.ac.rdg.resc.ncwms.config.Dataset;
import uk.ac.rdg.resc.ncwms.datareader.DataReader;
import uk.ac.rdg.resc.ncwms.datareader.HorizontalGrid;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidFormatException;
import uk.ac.rdg.resc.ncwms.exceptions.LayerNotQueryableException;
import uk.ac.rdg.resc.ncwms.exceptions.OperationNotSupportedException;
import uk.ac.rdg.resc.ncwms.exceptions.Wms1_1_1Exception;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;
import uk.ac.rdg.resc.ncwms.graphics.ImageFormat;
import uk.ac.rdg.resc.ncwms.graphics.KmzFormat;
import uk.ac.rdg.resc.ncwms.usagelog.UsageLogger;
import uk.ac.rdg.resc.ncwms.metadata.Layer;
import uk.ac.rdg.resc.ncwms.metadata.MetadataStore;
import uk.ac.rdg.resc.ncwms.metadata.TimestepInfo;
import uk.ac.rdg.resc.ncwms.metadata.VectorLayer;
import uk.ac.rdg.resc.ncwms.styles.ColorPalette;
import uk.ac.rdg.resc.ncwms.styles.ImageProducer;
import uk.ac.rdg.resc.ncwms.usagelog.UsageLogEntry;
import uk.ac.rdg.resc.ncwms.utils.WmsUtils;

/**
 * <p>This Controller is the entry point for all standard WMS operations
 * (GetMap, GetCapabilities, GetFeatureInfo).  Only one WmsController object 
 * is created.  Spring manages the creation of this object and the injection 
 * of the objects that it needs (i.e. its dependencies), such as the
 * {@linkplain MetadataStore store of metadata} and the
 * {@linkplain Config configuration object}.  The Spring configuration file <tt>web/WEB-INF/WMS-servlet.xml</tt>
 * defines all this information and also defines that this Controller will handle
 * all requests to the URI pattern <tt>/wms</tt>.  (See the SimpleUrlHandlerMapping
 * in <tt>web/WEB-INF/WMS-servlet.xml</tt>).</p>
 *
 * <p>See the {@link #handleRequestInternal handleRequestInternal()}
 * method for more information.</p>
 *
 * <p><i>(Note that we cannot use a CommandController here
 * because there is no (apparent) way in Spring to use case-insensitive parameter
 * names to bind request parameters to an object.)</i></p>
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class WmsController extends AbstractController
{
    private static final Logger log = Logger.getLogger(WmsController.class);
    
    /**
     * The maximum number of layers that can be requested in a single GetMap
     * operation
     */
    private static final int LAYER_LIMIT = 1;
    
    private static final String FEATURE_INFO_XML_FORMAT = "text/xml";
    private static final String FEATURE_INFO_PNG_FORMAT = "image/png";
    
    // These objects will be injected by Spring
    private Config config;
    private MetadataStore metadataStore;
    private UsageLogger usageLogger;
    private MetadataController metadataController;
    private TileCache tileCache;
    
    /**
     * Called automatically by Spring
     */
    public void init()
    {
        // We initialize the ColorPalettes.  We need to do this from here
        // because we need a way to find out the real path of the 
        // directory containing the palettes.  Therefore we need a way of 
        // getting at the ServletContext object, which isn't available from
        // the ColorPalette class.
        String paletteLocation = this.getWebApplicationContext()
            .getServletContext().getRealPath("/WEB-INF/conf/palettes");
        File paletteLocationDir = new File(paletteLocation);
        if (paletteLocationDir.exists() && paletteLocationDir.isDirectory())
        {
            ColorPalette.loadPalettes(paletteLocationDir);
        }
        else
        {
            logger.info("Directory of palette files does not exist or is not a directory");
        }
    }
    
    /**
     * <p>Entry point for all requests to the WMS.  This method first 
     * creates a <tt>RequestParams</tt> object from the URL query string.  This
     * object provides methods for retrieving parameter values, based on the fact that
     * WMS parameter <i>names</i> are case-insensitive.</p>
     * 
     * <p>Based on the value of the
     * REQUEST parameter this method then delegates to
     * {@link #getCapabilities getCapabilities()}, {@link #getMap getMap()}
     * or {@link #getFeatureInfo getFeatureInfo()}.
     * If the information returned from
     * this method is to be presented as an XML/JSON/HTML document, the method returns
     * a ModelAndView object containing the name of a JSP page and the data that the JSP
     * needs to render.  If the information is to be presented as an image, the method
     * writes the image to the servlet's output stream, then returns null.</p>
     *
     * <p>Any Exceptions that are thrown by this method or its delegates are 
     * automatically handled by Spring and converted to XML to be presented to the
     * user.  See the <a href="../exceptions/package-summary.html">Exceptions package</a>
     * for more details.</p>
     */
    protected ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse) throws Exception
    {
        UsageLogEntry usageLogEntry = new UsageLogEntry(httpServletRequest);
        boolean logUsage = true;
        // Create an object that allows request parameters to be retrieved in
        // a way that is not sensitive to the case of the parameter NAMES
        // (but is sensitive to the case of the parameter VALUES).
        RequestParams params = new RequestParams(httpServletRequest.getParameterMap());
        try
        {
            // Check the REQUEST parameter to see if we're producing a capabilities
            // document, a map or a FeatureInfo
            String request = params.getMandatoryString("request");
            usageLogEntry.setWmsOperation(request);
            if (request.equals("GetCapabilities"))
            {
                return getCapabilities(params, httpServletRequest, usageLogEntry);
            }
            else if (request.equals("GetMap"))
            {
                return getMap(params, httpServletResponse, usageLogEntry);
            }
            else if (request.equals("GetFeatureInfo"))
            {
                return getFeatureInfo(params, httpServletRequest, httpServletResponse,
                    usageLogEntry);
            }
            // The REQUESTs below are non-standard and could be refactored into
            // a different servlet endpoint
            else if (request.equals("GetMetadata"))
            {
                // This is a request for non-standard metadata.  (This will one
                // day be replaced by queries to Capabilities fragments, if possible.)
                // Delegate to the MetadataController
                return this.metadataController.handleRequest(httpServletRequest,
                    httpServletResponse, usageLogEntry);
            }
            else if (request.equals("GetLegendGraphic"))
            {
                // This is a request for an image that contains the colour scale
                // and range for a given layer
                return getLegendGraphic(params, httpServletResponse);
            }
            else if (request.equals("GetKML"))
            {
                // This is a request for a KML document that allows the selected
                // layer(s) to be displayed in Google Earth in a manner that 
                // supports region-based overlays.  Note that this is distinct
                // from simply setting "KMZ" as the output format of a GetMap
                // request: GetKML will give generally better results, but relies
                // on callbacks to this server.  Requesting KMZ files from GetMap
                // returns a standalone KMZ file.
                return getKML(params, httpServletRequest, httpServletResponse,
                    usageLogEntry);
            }
            else if (request.equals("GetKMLRegion"))
            {
                // This is a request for a particular sub-region from Google Earth.
                logUsage = false; // We don't log usage for this operation
                return getKMLRegion(params, httpServletRequest, httpServletResponse);
            }
            else
            {
                throw new OperationNotSupportedException(request);
            }
        }
        catch(WmsException wmse)
        {
            // We don't log these errors
            usageLogEntry.setException(wmse);
            String wmsVersion = params.getWmsVersion();
            if (wmsVersion != null && wmsVersion.equals("1.1.1"))
            {
                // We create a new exception type to ensure that the correct
                // JSP is used to render it.  This class also translates any
                // exception codes that are different in 1.1.1 (i.e. InvalidCRS/SRS)
                throw new Wms1_1_1Exception(wmse);
            }
            throw wmse;
        }
        catch(Exception e)
        {
            log.error(e.getMessage(), e);
            usageLogEntry.setException(e);
            throw e;
        }
        finally
        {
            if (logUsage)
            {
                // Log this request to the usage log
                this.usageLogger.logUsage(usageLogEntry);
            }
        }
    }
    
    /**
     * Executes the GetCapabilities operation, returning a ModelAndView for
     * display of the information as an XML document.  If the user has
     * requested VERSION=1.1.1 the information will be rendered using
     * <tt>web/WEB-INF/jsp/capabilities_xml_1_1_1.jsp</tt>.  If the user
     * specifies VERSION=1.3.0 (or does not specify a version) the information
     * will be rendered using <tt>web/WEB-INF/jsp/capabilities_xml.jsp</tt>.
     */
    protected ModelAndView getCapabilities(RequestParams params,
        HttpServletRequest httpServletRequest, UsageLogEntry usageLogEntry)
        throws WmsException
    {
        // Check the SERVICE parameter
        String service = params.getMandatoryString("service");
        if (!service.equals("WMS"))
        {
            throw new WmsException("The value of the SERVICE parameter must be \"WMS\"");
        }
        
        // Check the VERSION parameter (not compulsory for GetCapabilities)
        String version = params.getWmsVersion();
        usageLogEntry.setWmsVersion(version);
        
        // Check the FORMAT parameter
        String format = params.getString("format");
        usageLogEntry.setOutputFormat(format);
        // The WMS 1.3.0 spec says that we can respond with the default text/xml
        // format if the client has requested an unknown format.  Hence we do
        // nothing here.
        
        // TODO: check the UPDATESEQUENCE parameter
        
        // The DATASET parameter is an optional parameter that allows a 
        // Capabilities document to be generated for a single dataset only
        String datasetId = params.getString("dataset");
        Collection<Dataset> datasets;
        if (datasetId == null || datasetId.trim().equals(""))
        {
            // No specific dataset has been chosen so we create a Capabilities
            // document including every dataset
            datasets = this.config.getDatasets().values();
        }
        else
        {
            // Look for this dataset
            Dataset ds = this.config.getDatasets().get(datasetId);
            if (ds == null)
            {
                throw new WmsException("There is no dataset with ID " + datasetId);
            }
            datasets = new ArrayList<Dataset>(1);
            datasets.add(ds);
        }
        
        Map<String, Object> models = new HashMap<String, Object>();
        models.put("config", this.config);
        models.put("datasets", datasets);
        models.put("wmsBaseUrl", httpServletRequest.getRequestURL().toString());
        // Show only a subset of the CRS codes that we are likely to use.
        // Otherwise Capabilities doc gets very large indeed.
        // TODO: make configurable in admin app
        String[] supportedCrsCodes = new String[]{
            "EPSG:4326", "CRS:84", // Plate Carree
            "EPSG:41001",          // Mercator (~ Google Maps)
            "EPSG:27700"           // British National Grid
            // TODO: what is polar stereographic?
        };
        models.put("supportedCrsCodes", supportedCrsCodes); //HorizontalGrid.SUPPORTED_CRS_CODES);
        models.put("supportedImageFormats", ImageFormat.getSupportedMimeTypes());
        models.put("layerLimit", LAYER_LIMIT);
        models.put("featureInfoFormats", new String[]{FEATURE_INFO_PNG_FORMAT,
            FEATURE_INFO_XML_FORMAT});
        models.put("legendWidth", ColorPalette.LEGEND_WIDTH);
        models.put("legendHeight", ColorPalette.LEGEND_HEIGHT);
        models.put("paletteNames", ColorPalette.getAvailablePaletteNames());
        if (version == null || version.equals("1.3.0"))
        {
            return new ModelAndView("capabilities_xml", models);
        }
        else if (version.equals("1.1.1"))
        {
            return new ModelAndView("capabilities_xml_1_1_1", models);
        }
        else
        {
            // TODO: do version negotiation properly
            throw new WmsException("Version " + version + " is not supported by this server");
        }
    }
    
    /**
     * Executes the GetMap operation.  This methods performs the following steps:
     * <ol>
     * <li>Creates a {@link GetMapRequest} object from the given {@link RequestParams}.
     * This parses the parameters and checks their validity.</li>
     * <li>Finds the relevant {@link Layer} object from the {@link MetadataStore}.</li>
     * <li>Creates a {@link HorizontalGrid} object that represents the grid on
     * which the final image will sit (based on the requested CRS and image
     * width/height).</li>
     * <li>Looks for TIME and ELEVATION parameters (TIME may be expressed as a
     * start/end range, in which case we will produce an animation).</li>
     * <li>Extracts the data using
     * {@link uk.ac.rdg.resc.ncwms.datareader.DataReader#read DataReader.read()}.
     * This returns an array of floats, representing
     * the data values at each pixel in the final image.</li>
     * <li>Uses an {@link ImageProducer} object to turn the array of data into
     * a {@link java.awt.image.BufferedImage} (or, in the case of an animation, several
     * {@link java.awt.image.BufferedImage}s).</li>
     * <li>Uses a {@link ImageFormat} object to write the image to the servlet's
     * output stream in the requested format.</li>
     * </ol>
     * @throws WmsException if the user has provided invalid parameters
     * @throws Exception if an internal error occurs
     * @see uk.ac.rdg.resc.ncwms.datareader.DataReader#read DataReader.read()
     * @see uk.ac.rdg.resc.ncwms.datareader.DefaultDataReader#read DefaultDataReader.read()
     * @todo Separate Model and View code more cleanly
     */
    protected ModelAndView getMap(RequestParams params, 
        HttpServletResponse httpServletResponse, UsageLogEntry usageLogEntry)
        throws WmsException, Exception
    {
        // Parse the URL parameters
        GetMapRequest getMapRequest = new GetMapRequest(params);
        usageLogEntry.setGetMapRequest(getMapRequest);

        // Get the ImageFormat object corresponding with the requested MIME type
        String mimeType = getMapRequest.getStyleRequest().getImageFormat();
        // This throws an InvalidFormatException if the MIME type is not supported
        ImageFormat imageFormat = ImageFormat.get(mimeType);

        GetMapDataRequest dr = getMapRequest.getDataRequest();
        String[] layers = dr.getLayers();
        if (layers.length > LAYER_LIMIT)
        {
            throw new WmsException("You may only request a maximum of " +
                WmsController.LAYER_LIMIT + " layer(s) simultaneously from this server");
        }
        // TODO: support more than one layer
        Layer layer = this.metadataStore.getLayerByUniqueName(layers[0]);
        usageLogEntry.setLayer(layer);

        // Get the grid onto which the data will be projected
        HorizontalGrid grid = new HorizontalGrid(dr);

        // Create an object that will turn data into BufferedImages
        ImageProducer imageProducer = new ImageProducer(getMapRequest, layer);
        // Need to make sure that the images will be compatible with the
        // requested image format
        if (imageProducer.isTransparent() && !imageFormat.supportsFullyTransparentPixels())
        {
            throw new WmsException("The image format " + mimeType +
                " does not support fully-transparent pixels");
        }
        if (imageProducer.getOpacity() < 100 && !imageFormat.supportsPartiallyTransparentPixels())
        {
            throw new WmsException("The image format " + mimeType +
                " does not support partially-transparent pixels");
        }

        String zValue = dr.getElevationString();
        int zIndex = getZIndex(zValue, layer); // -1 if no z axis present

        // Cycle through all the provided timesteps, extracting data for each step
        List<String> tValues = new ArrayList<String>();
        String timeString = getMapRequest.getDataRequest().getTimeString();
        List<Integer> tIndices = getTIndices(timeString, layer);
        if (tIndices.size() > 1 && !imageFormat.supportsMultipleFrames())
        {
            throw new WmsException("The image format " + mimeType +
                " does not support multiple frames");
        }
        usageLogEntry.setNumTimeSteps(tIndices.size());
        long beforeExtractData = System.currentTimeMillis();
        for (int tIndex : tIndices)
        {
            // tIndex == -1 if there is no t axis present
            List<float[]> picData = readData(layer, tIndex, zIndex, grid,
                this.tileCache, usageLogEntry);
            // Only add a label if this is part of an animation
            String tValue = "";
            if (layer.isTaxisPresent() && tIndices.size() > 1)
            {
                tValue = WmsUtils.dateToISO8601(layer.getTimesteps().get(tIndex).getDate());
            }
            tValues.add(tValue);
            imageProducer.addFrame(picData, tValue); // the tValue is the label for the image
        }
        long timeToExtractData = System.currentTimeMillis() - beforeExtractData;
        usageLogEntry.setTimeToExtractDataMs(timeToExtractData);
        
        // We only create a legend object if the image format requires it
        BufferedImage legend = imageFormat.requiresLegend() ? imageProducer.getLegend() : null;

        // Write the image to the client
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        httpServletResponse.setContentType(mimeType);
        // If this is a KMZ file give it a sensible filename
        if (imageFormat instanceof KmzFormat)
        {
            httpServletResponse.setHeader("Content-Disposition", "inline; filename=" +
                layer.getDataset().getId() + "_" + layer.getId() + ".kmz");
        }

        // Send the images to the picMaker and write to the output
        imageFormat.writeImage(imageProducer.getRenderedFrames(),
            httpServletResponse.getOutputStream(), layer, tValues, zValue,
            grid.getBbox(), legend);

        return null;
    }
    
    /**
     * Reads data from the given variable, returning a List of data arrays.
     * This List will have a single element if the variable is scalar, or two
     * elements if the variable is a vector
     */
    static List<float[]> readData(Layer layer, int tIndex, int zIndex,
        HorizontalGrid grid, TileCache tileCache, UsageLogEntry usageLogEntry)
        throws Exception
    {
        List<float[]> picData = new ArrayList<float[]>();
        if (layer instanceof VectorLayer)
        {
            VectorLayer vecLayer = (VectorLayer)layer;
            picData.add(readDataArray(vecLayer.getEastwardComponent(), tIndex,
                zIndex, grid, tileCache, usageLogEntry));
            picData.add(readDataArray(vecLayer.getNorthwardComponent(), tIndex,
                zIndex, grid, tileCache, usageLogEntry));
        }
        else
        {
            picData.add(readDataArray(layer, tIndex, zIndex, grid, tileCache,
                usageLogEntry));
        }
        return picData;
    }
    
    /**
     * Reads an array of data from a Layer that is <b>not</b> a VectorLayer.
     * @param usageLogEntry a UsageLogEntry that is used to collect information
     * about the usage of this WMS (may be null, e.g. if this is being called
     * from the MetadataLoader).
     */
    private static float[] readDataArray(Layer layer, int tIndex, int zIndex,
        HorizontalGrid grid, TileCache tileCache, UsageLogEntry usageLogEntry)
        throws Exception
    {
        // Get a DataReader object for reading the data
        String dataReaderClass = layer.getDataset().getDataReaderClass();
        String location = layer.getDataset().getLocation();
        DataReader dr = DataReader.getDataReader(dataReaderClass, location);
        log.debug("Got data reader of type {}", dr.getClass().getName());
        
        // See exactly which file we're reading from, and which time index in 
        // the file (handles datasets with glob aggregation)
        String filename;
        int tIndexInFile;
        if (tIndex >= 0)
        {
            TimestepInfo tInfo = layer.getTimesteps().get(tIndex);
            filename = tInfo.getFilename();
            tIndexInFile = tInfo.getIndexInFile();
        }
        else
        {
            // There is no time axis
            // TODO: this fails if there is a layer in the dataset which 
            // has no time axis but the dataset is still a glob aggregation
            // (e.g. a bathymetry layer that is present in every file in the glob
            // aggregation, but has no time dependence).
            filename = layer.getDataset().getLocation();
            tIndexInFile = tIndex;
        }
        float[] data = null;
        TileCacheKey key = null;
        if (tileCache != null)
        {
            // Check the cache to see if we've already extracted this data
            // The TileCacheKey class stores different information depending
            // on whether the file in question is a local file, an OPeNDAP
            // endpoint or an NcML aggregation (see the Javadoc for the TileCache
            // class)
            key = new TileCacheKey(filename, layer, grid, tIndexInFile, zIndex);
            // Try to get the data from cache
            data = tileCache.get(key);
        }
        if (data == null)
        {
            // Data not found in cache or cache not present
            data = dr.read(filename, layer, tIndexInFile, zIndex, grid);
            // Put the data into the cache
            if (tileCache != null) tileCache.put(key, data);
            if (usageLogEntry != null) usageLogEntry.setUsedCache(false);
        }
        else
        {
            // Data were found in the cache
            if (usageLogEntry != null) usageLogEntry.setUsedCache(true);
        }
        
        return data;
    }
    
    /**
     * Executes the GetFeatureInfo operation
     * @throws WmsException if the user has provided invalid parameters
     * @throws Exception if an internal error occurs
     * @todo Separate Model and View code more cleanly
     */
    protected ModelAndView getFeatureInfo(RequestParams params,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse,
        UsageLogEntry usageLogEntry)
        throws WmsException, Exception
    {
        // Look to see if we're requesting data from a remote server
        String url = params.getString("url");
        if (url != null && !url.trim().equals(""))
        {
            usageLogEntry.setRemoteServerUrl(url);
            MetadataController.proxyRequest(url, httpServletRequest, httpServletResponse);
            return null;
        }
        
        GetFeatureInfoRequest request = new GetFeatureInfoRequest(params);
        usageLogEntry.setGetFeatureInfoRequest(request);
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
        
        // Get the layer we're interested in
        String layerName = dataRequest.getLayers()[0];
        Layer layer = this.metadataStore.getLayerByUniqueName(layerName);
        usageLogEntry.setLayer(layer);
        if (!layer.isQueryable())
        {
            throw new LayerNotQueryableException(layerName);
        }
        
        // Get the grid onto which the data is being projected
        HorizontalGrid grid = new HorizontalGrid(dataRequest);
        // Get the x and y values of the point of interest
        double x = grid.getXAxisValues()[dataRequest.getPixelColumn()];
        double y = grid.getYAxisValues()[dataRequest.getPixelRow()];
        LatLonPoint latLon = grid.transformToLatLon(x, y);
        usageLogEntry.setFeatureInfoLocation(latLon.getLongitude(), latLon.getLatitude());
        
        // Get the index along the z axis
        int zIndex = getZIndex(dataRequest.getElevationString(), layer); // -1 if no z axis present
        
        // Get the information about the requested timesteps
        List<Integer> tIndices = getTIndices(dataRequest.getTimeString(), layer);
        usageLogEntry.setNumTimeSteps(tIndices.size());
        
        // Now read the data, mapping date-times to data values
        // The map is sorted in order of ascending time
        SortedMap<Date, Float> featureData = new TreeMap<Date, Float>();
        for (int tIndex : tIndices)
        {
            Date date = tIndex < 0 ? null : layer.getTimesteps().get(tIndex).getDate();
            
            // Create a trivial Grid for reading a single point of data.
            // We use the same coordinate reference system as the original request
            HorizontalGrid singlePointGrid = new HorizontalGrid(dataRequest.getCrsCode(),
                1, 1, new double[]{x, y, x, y});
            
            float val;
            // We don't use the tile cache for getFeatureInfo
            if (layer instanceof VectorLayer)
            {
                VectorLayer vecLayer = (VectorLayer)layer;
                float xval = readDataArray(vecLayer.getEastwardComponent(), tIndex,
                    zIndex, singlePointGrid, null, usageLogEntry)[0];
                float yval = readDataArray(vecLayer.getNorthwardComponent(), tIndex,
                    zIndex, singlePointGrid, null, usageLogEntry)[0];
                val = (float)Math.sqrt(xval * xval + yval * yval);
            }
            else
            {
                val = readDataArray(layer, tIndex, zIndex, singlePointGrid, null,
                    usageLogEntry)[0];
            }
            featureData.put(date, Float.isNaN(val) ? null : val);
        }
        
        if (request.getOutputFormat().equals(FEATURE_INFO_XML_FORMAT))
        {
            Map<String, Object> models = new HashMap<String, Object>();
            models.put("longitude", latLon.getLongitude());
            models.put("latitude", latLon.getLatitude());
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
            String title = "Lon: " + latLon.getLongitude() + ", Lat: " + latLon.getLatitude();
            String yLabel = layer.getTitle() + " (" + layer.getUnits() + ")";
            JFreeChart chart = ChartFactory.createTimeSeriesChart(title,
                "Date / time", yLabel, xydataset, false, false, false);
            httpServletResponse.setContentType("image/png");
            ChartUtilities.writeChartAsPNG(httpServletResponse.getOutputStream(), chart, 400, 300);
            return null;
        }
    }

    /**
     * Creates and returns a PNG image with the colour scale and range for 
     * a given Layer
     */
    private ModelAndView getLegendGraphic(RequestParams params,
        HttpServletResponse httpServletResponse) throws Exception
    {
        BufferedImage legend;
        
        // Find the requested colour palette, or use the default if not set
        ColorPalette palette = ColorPalette.get(params.getString("palette"));
        // numColourBands defaults to 254 (the maximum) if not set
        int numColourBands = GetMapStyleRequest.getNumColourBands(params);
        
        // Find out if we just want the colour bar with no supporting text
        String colorBarOnly = params.getString("colorbaronly", "false");
        if (colorBarOnly.equalsIgnoreCase("true"))
        {
            // We're only creating the colour bar so we need to know a width
            // and height
            int width = params.getPositiveInt("width", 50);
            int height = params.getPositiveInt("height", 200);
            legend = palette.createColorBar(width, height, numColourBands);
        }
        else
        {
            // We're creating a legend with supporting text so we need to know
            // the colour scale range and the layer in question
            String layerName = params.getMandatoryString("layer");
            Layer layer = this.metadataStore.getLayerByUniqueName(layerName);
            float[] colourScaleRange = GetMapStyleRequest.getColourScaleRange(params);
            // Get the legend graphic and write it to the client
            // The scale range will be [0,0] if the client has not specified one
            // explicitly.  In this case createLegend() will use the layer's
            // default scale range.
            legend = palette.createLegend(numColourBands, layer,
                colourScaleRange[0], colourScaleRange[1]);
        }
        httpServletResponse.setContentType("image/png");
        ImageIO.write(legend, "png", httpServletResponse.getOutputStream());
        
        return null;
    }

    private ModelAndView getKML(RequestParams params,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse,
        UsageLogEntry usageLogEntry) throws Exception
    {
        // Get the Layer objects that we are to include in the KML.  The layer
        // objects are bundled with information about the top-level tiles that
        // need to be created in the top-level KML
        List<TiledLayer> tiledLayers = new ArrayList<TiledLayer>();
        for (String layerName : params.getMandatoryString("layers").split(","))
        {
            Layer layer = this.metadataStore.getLayerByUniqueName(layerName);
            // The data will be displayed on Google Earth using tiles.  To take
            // best advantage of the tile cache, we want to make sure that the
            // tiles match those that will be generated by the Godiva2 site.
            
            // The widest relevant zoom level (which we shall call level zero)
            // in Godiva2 covers the earth with two
            // tiles, one for the eastern and one for the western hemisphere, i.e.
            // a side length of 180 degrees.  Each zoom level halves the side length.
            // l = 180 / 2^z (l = side length, z = zoom level)
            
            // We need to calculate the widest zoom level that is relevant for this
            // layer.  This is the zoom level that will allow the layer to be
            // displayed with a maximum of four tiles.  Then the sub-regions will
            // be calculated automatically in getKMLRegion().
            
            // First we need to calculate the length of the longest side of the
            // bounding box in degrees.  (We know that layer.getBbox() returns
            // values in lat/lon degrees.)
            double[] bbox = layer.getBbox();
            log.debug("Layer bbox = {},{},{},{}", new Object[]{
                bbox[0], bbox[1], bbox[2], bbox[3]});
            double longestBboxSideLength = Math.max(
                bbox[2] - bbox[0],
                bbox[3] - bbox[1]
            );
            log.debug("Longest side length = {}", longestBboxSideLength);
            // z = ln(180/l) / ln(2)
            double zoom = Math.log(180.0 / longestBboxSideLength) / Math.log(2.0);
            // Need to take the floor of this number to ensure that the tiles
            // are big enough (remember the lower the z value, the larger the tiles)
            int z = (int)Math.floor(zoom);
            if (z < 0) z = 0; // Don't want to zoom out any further
            log.debug("Zoom level = {}", z);
            // Calculate the side length at this zoom level
            double sideLength = 180.0 / Math.pow(2, z);
            log.debug("Side length at this zoom level = {}", sideLength);
            assert(sideLength >= longestBboxSideLength);
            // Calculate the index of the tile in the horizontal direction
            // that contains the left-hand edge of the bounding box of the layer
            // The first tile (with index 0) is the one whose LH edge is at -180 degrees
            int leftIndex   = (int)((bbox[0] + 180.0) / sideLength);
            int rightIndex  = (int)((bbox[2] + 180.0) / sideLength);
            // Similarly for the bottom and top edges of the layer's bbox
            int bottomIndex = (int)((bbox[1] +  90.0) / sideLength);
            int topIndex    = (int)((bbox[3] +  90.0) / sideLength);
            log.debug("Indices: L={}, R={}, B={}, T={}", new Object[]{
                leftIndex, rightIndex, bottomIndex, topIndex});
            // Create bounding boxes for the tiles that cover the layer
            List<double[]> tiles = new ArrayList<double[]>();
            for (int j = bottomIndex; j <= topIndex; j++)
            {
                double bottom = sideLength * j - 90.0;
                double top = bottom + sideLength;
                for (int i = leftIndex; i <= rightIndex; i++)
                {
                    double left = sideLength * i - 180.0;
                    double right = left + sideLength;
                    log.debug("Adding new tile({},{},{},{})", new Object[]{
                        left, bottom, right, top});
                    tiles.add(new double[]{left, bottom, right, top});
                }
            }
            assert(tiles.size() <= 4);
            log.debug("Created {} tiles for layer {}", tiles.size(), layer.getLayerName());
            tiledLayers.add(new TiledLayer(layer, tiles));
        }
        
        Map<String, Object> models = new HashMap<String, Object>();
        models.put("tiledLayers", tiledLayers);
        models.put("title", this.config.getServer().getTitle());
        models.put("description", this.config.getServer().getAbstract());
        models.put("wmsBaseUrl", httpServletRequest.getRequestURL().toString());
        return new ModelAndView("topLevelKML", models);
    }
    
    private ModelAndView getKMLRegion(RequestParams params,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse) throws Exception
    {
        Layer layer = this.metadataStore.getLayerByUniqueName(params.getMandatoryString("layer"));
        double[] dbox = WmsUtils.parseBbox(params.getMandatoryString("dbox"));
        // Calculate the bounding boxes of all the four sub-regions
        double[][] regionDBoxes = new double[4][4];
        double halfLon = (dbox[2] - dbox[0]) / 2.0;
        double halfLat = (dbox[3] - dbox[1]) / 2.0;
        
        regionDBoxes[0][0] = dbox[0];
        regionDBoxes[0][1] = dbox[1];
        regionDBoxes[0][2] = dbox[0] + halfLon;
        regionDBoxes[0][3] = dbox[1] + halfLat;
        
        regionDBoxes[1][0] = dbox[0] + halfLon;
        regionDBoxes[1][1] = dbox[1];
        regionDBoxes[1][2] = dbox[2];
        regionDBoxes[1][3] = dbox[1] + halfLat;
        
        regionDBoxes[2][0] = dbox[0];
        regionDBoxes[2][1] = dbox[1] + halfLat;
        regionDBoxes[2][2] = dbox[0] + halfLon;
        regionDBoxes[2][3] = dbox[3];
        
        regionDBoxes[3][0] = dbox[0] + halfLon;
        regionDBoxes[3][1] = dbox[1] + halfLat;
        regionDBoxes[3][2] = dbox[2];
        regionDBoxes[3][3] = dbox[3];
        
        Map<String, Object> models = new HashMap<String, Object>();
        models.put("layer", layer);
        models.put("elevation", params.getString("elevation"));
        models.put("time", params.getString("time"));
        models.put("dbox", dbox);
        models.put("size", 256);
        models.put("regionDBoxes", regionDBoxes);
        models.put("wmsBaseUrl", httpServletRequest.getRequestURL().toString());
        return new ModelAndView("regionBasedOverlay", models);
    }
    
    /**
     * @return the index on the z axis of the requested Z value.  Returns 0 (the
     * default) if no value has been specified and the provided Variable has a z
     * axis.  Returns -1 if no value is needed because there is no z axis in the
     * data.
     * @throws InvalidDimensionValueException if the provided z value is not
     * a valid floating-point number or if it is not a valid value for this axis.
     */
    static int getZIndex(String zValue, Layer layer)
        throws InvalidDimensionValueException
    {
        // Get the z value.  The default value is the first value in the array
        // of z values
        if (zValue == null)
        {
            // No value has been specified
            return layer.isZaxisPresent() ? layer.getDefaultZIndex() : -1;
        }
        // The user has specified a z value.  Check that we have a z axis
        if (!layer.isZaxisPresent())
        {
            return -1; // We ignore the given value
        }
        // Check to see if this is a single value (the
        // user hasn't requested anything of the form z1,z2 or start/stop/step)
        if (zValue.split(",").length > 1 || zValue.split("/").length > 1)
        {
            throw new InvalidDimensionValueException("elevation", zValue);
        }
        return layer.findZIndex(zValue);
    }
    
    /**
     * @return a List of indices along the time axis corresponding with the
     * requested TIME parameter.  If there is no time axis, this will return
     * a List with a single value of -1.
     */
    static List<Integer> getTIndices(String timeString, Layer layer)
        throws WmsException
    {
        List<Integer> tIndices = new ArrayList<Integer>();
        if (layer.isTaxisPresent())
        {
            if (timeString == null)
            {
                // The default time is the last value along the axis
                // TODO: this should be the time closest to now
                tIndices.add(layer.getDefaultTIndex());
            }
            else
            {
                // Interpret the time specification
                for (String t : timeString.split(","))
                {
                    String[] startStop = t.split("/");
                    if (startStop.length == 1)
                    {
                        // This is a single time value
                        tIndices.add(layer.findTIndex(startStop[0]));
                    }
                    else if (startStop.length == 2)
                    {
                        // Use all time values from start to stop inclusive
                        tIndices.addAll(layer.findTIndices(startStop[0], startStop[1]));
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
     * Called by Spring to inject the metadata controller
     */
    public void setMetadataController(MetadataController metadataController)
    {
        this.metadataController = metadataController;
    }
    
    /**
     * Called by the Spring framework to inject the config object
     */
    public void setConfig(Config config)
    {
        this.config = config;
    }
    
    /**
     * Called by Spring to inject the usage logger
     */
    public void setUsageLogger(UsageLogger usageLogger)
    {
        this.usageLogger = usageLogger;
    }
    
    /**
     * Called by Spring to inject the metadata store
     */
    public void setMetadataStore(MetadataStore metadataStore)
    {
        this.metadataStore = metadataStore;
    }
    
    /**
     * Called by Spring to inject the tile cache
     */
    public void setTileCache(TileCache tileCache)
    {
        this.tileCache = tileCache;
    }
}

/**
 * Represents a WMS version number.  Not used in the current code, but preserved
 * for future use in version negotiation.
 */
class WmsVersion implements Comparable<WmsVersion>
{
    private int x,y,z; // The three components of the version number
    
    public static final WmsVersion VERSION_1_1_1 = new WmsVersion("1.1.1");
    public static final WmsVersion VERSION_1_3_0 = new WmsVersion("1.3.0");
    
    /**
     * Creates a new WmsVersion object based on the given String
     * (e.g. "1.3.0")
     * @throws IllegalArgumentException if the given String does not represent
     * a valid WMS version number
     */
    public WmsVersion(String versionStr)
    {
        String[] els = versionStr.split(".");
        if (els.length != 3)
        {
            throw new IllegalArgumentException(versionStr +
                " is not a valid WMS version number");
        }
        try
        {
            this.x = Integer.parseInt(els[0]);
            this.y = Integer.parseInt(els[1]);
            this.z = Integer.parseInt(els[2]);
        }
        catch(NumberFormatException nfe)
        {
            throw new IllegalArgumentException(versionStr +
                " is not a valid WMS version number");
        }
        if (this.y > 99 || this.z > 99)
        {
            throw new IllegalArgumentException(versionStr +
                " is not a valid WMS version number");
        }
    }
    
    /**
     * Compares this WmsVersion with the specified Version for order.  Returns a
     * negative integer, zero, or a positive integer as this Version is less
     * than, equal to, or greater than the specified Version.
     */
    public int compareTo(WmsVersion otherVersion)
    {
        // Could also do this by calculating a single integer value for the
        // version: 100*100*x + 100*y + z
        if (this.x == otherVersion.x)
        {
            if (this.y == otherVersion.y)
            {
                return new Integer(this.z).compareTo(otherVersion.z);
            }
            else
            {
                return new Integer(this.y).compareTo(otherVersion.y);
            }
        }
        else
        {
            return new Integer(this.x).compareTo(otherVersion.x);
        }
    }
    
    /**
     * @return String representation of this version, e.g. "1.3.0"
     */
    @Override
    public String toString()
    {
        return this.x + "." + this.y + "." + this.z;
    }
}
