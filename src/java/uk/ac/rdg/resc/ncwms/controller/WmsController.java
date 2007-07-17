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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;
import uk.ac.rdg.resc.ncwms.config.Config;
import uk.ac.rdg.resc.ncwms.exceptions.OperationNotSupportedException;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;

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
    private Config config; // Will be injected by Spring
    
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
            return getCapabilities(params);
        }
        else if (request.equals("GetMap"))
        {
            
        }
        else if (request.equals("GetFeatureInfo"))
        {
            
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
    private ModelAndView getCapabilities(RequestParams params) throws WmsException
    {
        // Check the SERVICE parameter
        String service = params.getMandatoryParamValue("service");
        if (!service.equals("WMS"))
        {
            throw new WmsException("The value of the SERVICE parameter must be \"WMS\"");
        }
        
        // Check the VERSION parameter
        String version = params.getParamValue("version");
        // We do nothing else here because 1.3.0 is the only version we support
        
        // Check the FORMAT parameter
        String format = params.getParamValue("format");
        // The WMS1.3.0 spec says that we can respond with the default text/xml
        // format if the client has requested an unknown format.  Hence we do
        // nothing here.
        
        // TODO: check the UPDATESEQUENCE parameter
        
        return new ModelAndView("capabilities_xml", "config", this.config);
    }

    /**
     * Called by Spring to inject the configuration object
     */
    public void setConfig(Config config)
    {
        this.config = config;
    }
    
}
