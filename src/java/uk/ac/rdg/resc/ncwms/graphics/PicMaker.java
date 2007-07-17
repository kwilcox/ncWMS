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

package uk.ac.rdg.resc.ncwms.graphics;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Set;
import org.apache.log4j.Logger;
import uk.ac.rdg.resc.ncwms.datareader.VariableMetadata;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidFormatException;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;

/**
 * Abstract superclass of picture makers.  Subclasses must have a no-argument
 * constructor
 *
 * Makes a picture from an array of raw data, using a rainbow colour model.
 * Fill values are represented as transparent pixels and out-of-range values
 * are represented as black pixels.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public abstract class PicMaker
{
    private static final Logger logger = Logger.getLogger(PicMaker.class);
    /**
     * Maps MIME types to the required class of PicMaker
     */
    private static Hashtable<String, Class> picMakers;
    
    // Image MIME type
    protected String mimeType;
    // The variable metadata from which this picture was created
    protected VariableMetadata var;
    
    protected String[] tValues; // Array of time values, one for each frame
    protected String zValue;
    protected float[] bbox;
    protected BufferedImage legend; // If we need a legend, it will be stored here    
    
    static
    {
        picMakers = new Hashtable<String, Class>();
        picMakers.put("image/png", SimplePicMaker.class);
        picMakers.put("image/gif", GifMaker.class);
        picMakers.put("application/vnd.google-earth.kmz", KmzMaker.class);
    }
    
    /**
     * @return the image formats (MIME types) that can be produced as a
     * Set of Strings.
     */
    public static final Set<String> getSupportedImageFormats()
    {
        return picMakers.keySet();
    }
    
    /**
     * Creates a PicMaker object for the given mime type.  Creates a new PicMaker
     * object with each call.
     * 
     * @param mimeType The MIME type of the image that is required
     * @return A PicMaker object
     * @throws a {@link InvalidFormatException} if there isn't a PicMaker for
     * the given MIME type
     * @throws a {@link WmsException} if the PicMaker could not be created
     */
    public static PicMaker createPicMaker(String mimeType)
        throws InvalidFormatException, WmsException
    {
        Class clazz = picMakers.get(mimeType.trim());
        if (clazz == null)
        {
            throw new InvalidFormatException(mimeType);
        }
        try
        {
            PicMaker pm = (PicMaker)clazz.newInstance();
            // Some PicMakers support multiple MIME types
            pm.mimeType = mimeType;
            return pm;
        }
        catch (InstantiationException ie)
        {
            throw new WmsException("Internal error: could not create PicMaker "
                + "of type " + clazz.getName());
        }
        catch (IllegalAccessException iae)
        {
            throw new WmsException("Internal error: IllegalAccessException" +
                " when creating PicMaker of type " + clazz.getName());
        }
    }
    
    public String getMimeType()
    {
        return mimeType;
    }
    
    public VariableMetadata getVar()
    {
        return var;
    }
    
    public void setVar(VariableMetadata var)
    {
        this.var = var;
    }
    
    /**
     * @return true if this PicMaker needs a legend: if this is true then
     * AbstractStyle.createLegend() will be called.  This default implementation
     * returns false: subclasses must override if they want to provide a legend
     * (e.g. KmzMaker)
     */
    public boolean needsLegend()
    {
        return false;
    }
    
    /**
     * Encodes and writes the frames to the given OutputStream
     * @param frames The arrays of pixels that will be rendered
     * @param transparentColor The Color that is to be rendered transparent
     * (may be ignored in subclasses that understand the alpha channel)
     * @param out The {@link OutputStream} to which the image will be written
     * @throws IOException if there was an error writing the data
     */
    public abstract void writeImage(ArrayList<BufferedImage> frames, 
        OutputStream out) throws IOException;

    public String[] getTvalues()
    {
        return tValues;
    }

    public void setTvalues(String[] tValues)
    {
        this.tValues = tValues;
    }

    public String getZvalue()
    {
        return zValue;
    }

    public void setZvalue(String zValue)
    {
        this.zValue = zValue;
    }

    public float[] getBbox()
    {
        return bbox;
    }

    public void setBbox(float[] bbox)
    {
        this.bbox = bbox;
    }

    public BufferedImage getLegend()
    {
        return legend;
    }

    public void setLegend(BufferedImage legend)
    {
        this.legend = legend;
    }
    
}
