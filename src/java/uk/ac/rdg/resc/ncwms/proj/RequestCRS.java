/*
 * Copyright (c) 2006 The University of Reading
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

package uk.ac.rdg.resc.ncwms.proj;

import java.awt.geom.Rectangle2D;
import java.util.Iterator;
import ucar.unidata.geoloc.LatLonPoint;
import uk.ac.rdg.resc.ncwms.exceptions.WMSException;

/**
 * Abstract superclass for a requested map CRS (as opposed to a CRS
 * for source data).  A new object of this class will be created with each
 * client request.  Subclasses must provide a no-argument constructor.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public abstract class RequestCRS
{
    /**
     * The bounding box of the request in this CRS, as specified by the client
     */
    protected Rectangle2D bbox;
    /**
     * The width of the requested picture in pixels
     */
    protected int picWidth;
    /**
     * The height of the requested picture in pixels
     */
    protected int picHeight;
    
    /**
     * Sets the bounding box of this request
     * @param bboxStr String representing the bounding box in the form
     * "minx,miny,maxx,maxy"
     * @throws WMSException if the format of the bounding box is not correct
     */
    public void setBoundingBox(String bboxStr) throws WMSException
    {
        String[] bboxEls = bboxStr.split(",");
        if (bboxEls.length != 4)
        {
            throw new WMSException("Invalid bounding box format");
        }
        double[] bbox = new double[bboxEls.length];
        for (int i = 0; i < bboxEls.length; i++)
        {
            try
            {
                bbox[i] = Double.parseDouble(bboxEls[i]);
            }
            catch(NumberFormatException nfe)
            {
                throw new WMSException("Invalid number in bounding box");
            }
        }
        if (bbox[0] > bbox[2] || bbox[1] > bbox[3])
        {
            throw new WMSException("Invalid bounding box format");
        }
        // TODO: check that the bounding box values are valid
        this.bbox = new Rectangle2D.Double(bbox[0], bbox[1],
            (bbox[2] - bbox[0]), (bbox[3] - bbox[1]));
    }
    
    /**
     * Sets the dimensions of the requested picture in pixels
     * @param width The width of the requested picture in pixels
     * @param height The height of the requested picture in pixels
     */
    public void setPictureDimension(int width, int height)
    {
        this.picWidth = width;
        this.picHeight = height;
    }
    
    /**
     * @return the width of the picture in pixels
     */
    public int getPictureWidth()
    {
        return this.picWidth;
    }
    
    /**
     * @return the height of the picture in pixels
     */
    public int getPictureHeight()
    {
        return this.picHeight;
    }
    
    /**
     * @return an Iterator over all the lon-lat points in this projection in
     * the given bounding box.  The bounding box and picture dimensions will
     * have been set before this method is called.
     */
    public abstract Iterator<LatLonPoint> getLatLonPointIterator();
    
}