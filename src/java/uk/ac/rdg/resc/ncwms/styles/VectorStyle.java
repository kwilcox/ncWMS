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

package uk.ac.rdg.resc.ncwms.styles;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import org.apache.log4j.Logger;
import uk.ac.rdg.resc.ncwms.datareader.VariableMetadata;
import uk.ac.rdg.resc.ncwms.exceptions.StyleNotDefinedException;

/**
 * Style for plotting vector data as arrows
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class VectorStyle extends AbstractStyle
{
    private static final Logger logger = Logger.getLogger(VectorStyle.class);
    /**
     * The maximum length of arrows in pixels
     */
    private static final int MAX_ARROW_LENGTH = 20;
    
    private float unitsPerPixel; // The scale of the arrows
    
    /**
     * Creates a new instance of VectorStyle
     */
    public VectorStyle()
    {
        super("vector");
        this.unitsPerPixel = 0.0f;
    }

    public void setAttribute(String attName, String[] values) throws StyleNotDefinedException
    {
        if (attName.trim().equalsIgnoreCase("upp"))
        {
            if (values.length != 1)
            {
                throw new StyleNotDefinedException("Format error for \"upp\" attribute of "
                    + this.name + " style");
            }
            try
            {
                this.unitsPerPixel = Float.parseFloat(values[0]);
            }
            catch (NumberFormatException nfe)
            {
                throw new StyleNotDefinedException("Format error for \"upp\" attribute of "
                    + this.name + " style");
            }
        }
        else
        {
            throw new StyleNotDefinedException("Attribute " + attName + 
                " is not supported by the " + this.name + " style");
        }
    }

    protected BufferedImage createLegend(VariableMetadata var)
    {
        return null; // TODO:
    }

    protected void adjustScaleForFrame(float[][] data)
    {
        if (data.length != 2)
        {
            // Shouldn't happen: defensive programming
            throw new IllegalStateException("A vector style is only appropriate "
                + "for fields with two components");
        }
        // Find the longest arrow in the units of the data
        double longest = Double.MIN_VALUE;
        for (int i = 0; i < data[0].length; i++)
        {
            if (data[0][i] != this.fillValue)
            {
                double len = Math.sqrt(data[0][i] * data[0][i] + data[1][i] * data[1][i]);
                if (len > longest) longest = len;
            }
        }
        this.unitsPerPixel = (float)longest / MAX_ARROW_LENGTH;
    }

    protected void createImage(float[][] data, String label)
    {
        // TODO: IE can't display these image types - use indexed color model
        // Also doesn't work with GifMaker!!
        BufferedImage image = new BufferedImage(this.picWidth, this.picHeight,
            BufferedImage.TYPE_INT_ARGB);
        
        Graphics2D g = image.createGraphics();
        // TODO: control the colour of the arrows
        g.setColor(Color.RED);
        
        // Take every twentieth pixel in each direction
        logger.debug("Drawing vectors, unitsPerPixel = {}", this.unitsPerPixel);
        for (int i = 0; i < this.picWidth; i += MAX_ARROW_LENGTH)
        {
            for (int j = 0; j < this.picHeight; j += MAX_ARROW_LENGTH)
            {
                int dataIndex = j * this.picWidth + i;
                if (data[0][dataIndex] != this.fillValue)
                {
                    // Calculate the end point of the arrow
                    float iEnd = i + data[0][dataIndex] / this.unitsPerPixel;
                    float jEnd = j + data[1][dataIndex] / this.unitsPerPixel;
                    //logger.debug("i={}, j={}, dataIndex={}, east={}, north={}",
                    //    new Object[]{i, j, dataIndex, data[0][dataIndex], data[1][dataIndex]});
                    // Draw a dot representing the data location
                    g.fillOval(i - 2, j - 2, 4, 4);
                    // Draw a line representing the vector direction and magnitude
                    g.setStroke(new BasicStroke(2));
                    g.drawLine(i, j, Math.round(iEnd), Math.round(jEnd));
                    // Draw the arrow on the canvas
                    //drawArrow(g, i, j, Math.round(iEnd), Math.round(jEnd), 2);
                }
            }
        }
        
        this.renderedFrames.add(image);
    }

    protected boolean isAutoScale()
    {
        // return true if the scale has not been set
        return this.unitsPerPixel == 0.0f;
    }
    
    // http://forum.java.sun.com/thread.jspa?threadID=378460&tstart=135
    private static void drawArrow(Graphics2D g2d, int xCentre, int yCentre, int x, int y, float stroke)
    {
        double aDir = Math.atan2(xCentre - x, yCentre - y);
        g2d.setStroke(new BasicStroke(stroke));
        g2d.drawLine(x, y, xCentre, yCentre);
        g2d.setStroke(new BasicStroke(1.0f));
        Polygon tmpPoly = new Polygon();
        int i1 = 12 + (int)(stroke * 2);
        int i2 = 6 + (int)stroke;
        tmpPoly.addPoint(x, y);
        tmpPoly.addPoint(x + xCor(i1, aDir + 0.5), y + yCor(i1, aDir + 0.5));
        tmpPoly.addPoint(x + xCor(i2, aDir), y + yCor(i2, aDir));
        tmpPoly.addPoint(x + xCor(i1, aDir - 0.5), y + yCor(i1, aDir - 0.5));
        tmpPoly.addPoint(x, y);
        g2d.drawPolygon(tmpPoly);
        g2d.fillPolygon(tmpPoly);
    }
    
    private static int yCor(int len, double dir)
    {
        return (int)(len * Math.cos(dir));
    }
    
    private static int xCor(int len, double dir)
    {
        return (int)(len * Math.sin(dir));
    }
    
}
