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

import uk.ac.rdg.resc.ncwms.exceptions.WmsException;
import uk.ac.rdg.resc.ncwms.grids.AbstractGrid;

/**
 * Contains the parts of the GetMap request that pertain to data extraction,
 * i.e. independent of styling.
 * @todo Use this as a key for the tile cache?  Would need to normalise timeString
 * etc.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class GetMapDataRequest
{
    protected String[] layers;
    private String crs;
    private double[] bbox;
    private int width;
    private int height;
    private String timeString;
    private String elevationString;
    
    /**
     * Creates a new instance of GetMapDataRequest from the given parameters
     * @throws WmsException if the request is invalid
     */
    public GetMapDataRequest(RequestParams params, String version) throws WmsException
    {
        this.layers = params.getMandatoryString("layers").split(",");
        this.init(params, version);
    }
    
    /**
     * Constructor called by GetFeatureInfoDataRequest
     */
    protected GetMapDataRequest() {}
    
    /**
     * Initializes the parameters that are common to GetMap and GetFeatureInfo
     */
    protected void init(RequestParams params, String version) throws WmsException
    {
        // WMS1.3.0 uses "CRS", 1.1.1 uses "SRS".  This is a bit of a kludge
        this.crs = params.getMandatoryString(version.equals("1.3.0") ? "crs" : "srs");
        String[] bboxEls = params.getMandatoryString("bbox").split(",");
        // Check the validity of the bounding box
        if (bboxEls.length != 4)
        {
            throw new WmsException("Invalid bounding box format: need four elements");
        }
        this.bbox = new double[4];
        try
        {
            for (int i = 0; i < bbox.length; i++)
            {
                this.bbox[i] = Double.parseDouble(bboxEls[i]);
            }
        }
        catch(NumberFormatException nfe)
        {
            throw new WmsException("Invalid bounding box format: all elements must be numeric");
        }
        if (this.bbox[0] >= this.bbox[2] || this.bbox[1] >= this.bbox[3])
        {
            throw new WmsException("Invalid bounding box format");
        }
        this.width = params.getMandatoryPositiveInt("width");
        this.height = params.getMandatoryPositiveInt("height");
        this.timeString = params.getString("time");
        this.elevationString = params.getString("elevation");
    }

    public String[] getLayers()
    {
        return layers;
    }

    public String getCrs()
    {
        return crs;
    }

    public double[] getBbox()
    {
        return bbox;
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public String getTimeString()
    {
        return timeString;
    }

    public String getElevationString()
    {
        return elevationString;
    }
    
}
