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
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;

/**
 * Class that contains the parameters of the user's request.  Parameter names
 * are not case sensitive.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
class RequestParams
{
    private Map<String, String> paramMap = new HashMap<String, String>();
    
    /**
     * Creates a new RequestParams object from the given Map of parameter names
     * and values (normally gained from HttpServletRequest.getParameterMap()).
     * The Map matches parameter names (Strings) to parameter values (String
     * arrays).
     */
    public RequestParams(Map httpRequestParamMap)
    {
        for (Object name : httpRequestParamMap.keySet())
        {
            String[] values = (String[])httpRequestParamMap.get(name);
            assert values.length >= 1;
            this.paramMap.put(name.toString().toLowerCase(), values[0]);
        }
    }
    
    /**
     * Returns the value of the parameter with the given name, or null if the
     * parameter does not have a value.  This method is not sensitive to the case
     * of the parameter name.
     */
    public String getParamValue(String paramName)
    {
        return this.paramMap.get(paramName.toLowerCase());
    }
    
    /**
     * Returns the value of the parameter with the given name, throwing a
     * WmsException if the parameter does not exist.
     */
    public String getMandatoryParamValue(String paramName) throws WmsException
    {
        String value = this.getParamValue(paramName);
        if (value == null)
        {
            throw new WmsException("Must provide a value for parameter "
                + paramName.toUpperCase());
        }
        return value;
    }
    
    /**
     * Returns the value of the parameter with the given name, or the supplied
     * default value if the parameter does not exist.
     */
    public String getParamValue(String paramName, String defaultValue)
    {
        String value = this.getParamValue(paramName);
        if (value == null)
        {
            return defaultValue;
        }
        return value;
    }
    
}
