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

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import uk.ac.rdg.resc.ncwms.config.Config;
import uk.ac.rdg.resc.ncwms.datareader.VariableMetadata;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidFormatException;
import uk.ac.rdg.resc.ncwms.exceptions.OperationNotSupportedException;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;
import uk.ac.rdg.resc.ncwms.graphics.PicMaker;
import uk.ac.rdg.resc.ncwms.graphics.PicMakerFactory;
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
    /**
     * The maximum number of layers that can be requested in a single GetMap
     * operation
     */
    static final int LAYER_LIMIT = 1;
    /**
     * The fill value to use when reading data and making pictures
     */
    private static final float FILL_VALUE = Float.NaN;
    
    private Config config;                   // Will be injected by Spring
    private Factory<PicMaker> picMakerFactory; // ditto
    private Factory<AbstractStyle> styleFactory; // ditto
    private MetadataController metadataController; // ditto
    
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
        String request = params.getMandatoryParamValue("request");
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
        String service = params.getMandatoryParamValue("service");
        if (!service.equals("WMS"))
        {
            throw new WmsException("The value of the SERVICE parameter must be \"WMS\"");
        }
        
        // Check the VERSION parameter
        String version = params.getParamValue("version");
        // We do nothing else here because we only support one version
        
        // Check the FORMAT parameter
        String format = params.getParamValue("format");
        // The WMS 1.3.0 spec says that we can respond with the default text/xml
        // format if the client has requested an unknown format.  Hence we do
        // nothing here.
        
        // TODO: check the UPDATESEQUENCE parameter
        
        Map<String, Object> models = new HashMap<String, Object>();
        models.put("config", this.config);
        models.put("wmsBaseUrl", httpServletRequest.getRequestURL().toString());
        models.put("picMakerFactory", this.picMakerFactory);
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
        String version = params.getMandatoryParamValue("version");
        if (!version.equals(WmsUtils.getVersion()))
        {
            throw new WmsException("VERSION must be " + WmsUtils.VERSION);
        }
        
        String[] layers = params.getMandatoryParamValue("layers").split(",");
        if (layers.length > LAYER_LIMIT)
        {
            throw new WmsException("You may only request a maximum of " +
                LAYER_LIMIT + " layer(s) simultaneously from this server");
        }
        
        // TODO: support more than one layer
        VariableMetadata var = this.config.getVariable(layers[0]);
        
        String[] styles = params.getMandatoryParamValue("styles").split(",");
        // We must either have one style per layer or else an empty parameter: "STYLES="
        if (styles.length != layers.length && !styles.equals(""))
        {
            throw new WmsException("You must request exactly one STYLE per layer, or use"
               + " the default style for each layer with STYLES=");
        }
        AbstractStyle style = null;
        if (styles[0].equals(""))
        {
            // TODO We'll use the default style for this variable
        }
        else
        {
            // TODO Use the given style if supported by this variable
        }
        //style.setFillValue(FILL_VALUE);
        
        // RequestParser replaces pluses with spaces: we must change back
        // to parse the format correctly
        String mimeType = params.getMandatoryParamValue("format").replaceAll(" ", "+");
        // Get the PicMaker that corresponds with this MIME type
        PicMaker picMaker = this.picMakerFactory.createObject(mimeType);
        if (picMaker == null)
        {
            throw new InvalidFormatException("The image format " + mimeType + 
                " is not supported by this server");
        }
        
        // TODO: deal with EXCEPTIONS
                
        // Get the requested transparency and background colour for the layer
        /*String trans = params.getParamValue("transparent", "false").toLowerCase();
        if (trans.equals("false")) style.setTransparent(false);
        else if (trans.equals("true")) style.setTransparent(true);
        else throw new WmsException("The value of TRANSPARENT must be \"TRUE\" or \"FALSE\"");

        String bgc = params.getParamValue("bgcolor", "0xFFFFFF");
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
        
        float[] bbox = getBbox(params);*/

        return null;
    }
    
    /**
     * Gets the bounding box from the parameters as an array of four floats
     * @throws WmsException if there is an error in the format of the bounding box
     */
    private static final float[] getBbox(RequestParams params) throws WmsException
    {
        String[] bboxEls = params.getMandatoryParamValue("bbox").split(",");
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
        return bbox;
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
     * Called by Spring to inject the metadata controller
     */
    public void setMetadataController(MetadataController metadataController)
    {
        this.metadataController = metadataController;
    }
    
}
