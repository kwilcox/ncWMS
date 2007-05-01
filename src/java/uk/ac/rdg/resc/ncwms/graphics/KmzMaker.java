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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.log4j.Logger;
import uk.ac.rdg.resc.ncwms.datareader.VariableMetadata;

/**
 * Creates KMZ files for importing into Google Earth.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class KmzMaker extends PicMaker
{
    private static final Logger logger = Logger.getLogger(KmzMaker.class);
    
    private static final String PICNAME = "frame";
    private static final String PICEXT  = "png";
    private static final String COLOUR_SCALE_FILENAME = "colourscale.png";
    
    private static DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("0.#####");
    private static DecimalFormat SCIENTIFIC_FORMATTER = new DecimalFormat("0.###E0");

    public void writeImage(ArrayList<BufferedImage> frames, OutputStream out) throws IOException
    {
        StringBuffer kml = new StringBuffer();
        
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++)
        {
            if (frameIndex == 0)
            {
                // This is the first frame.  Add the KML header and folder metadata
                kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                kml.append(System.getProperty("line.separator"));
                kml.append("<kml xmlns=\"http://earth.google.com/kml/2.0\">");
                kml.append("<Folder>");
                kml.append("<visibility>1</visibility>");
                kml.append("<name>" + this.var.getDataset().getId() + ", " +
                    this.var.getId() + "</name>");
                kml.append("<description>" + this.var.getDataset().getTitle() + ", "
                    + this.var.getTitle() + ": " + this.var.getAbstract() +
                    "</description>");

                // Add the screen overlay containing the colour scale
                kml.append("<ScreenOverlay>");
                kml.append("<name>Colour scale</name>");
                kml.append("<Icon><href>" + COLOUR_SCALE_FILENAME + "</href></Icon>");
                kml.append("<overlayXY x=\"0\" y=\"1\" xunits=\"fraction\" yunits=\"fraction\"/>");
                kml.append("<screenXY x=\"0\" y=\"1\" xunits=\"fraction\" yunits=\"fraction\"/>");
                kml.append("<rotationXY x=\"0\" y=\"0\" xunits=\"fraction\" yunits=\"fraction\"/>");
                kml.append("<size x=\"0\" y=\"0\" xunits=\"fraction\" yunits=\"fraction\"/>");
                kml.append("</ScreenOverlay>");
            }
        
            kml.append("<GroundOverlay>");
            String timestamp = null;
            String z = null;
            if (tValue != null && !tValue.equals(""))
            {
                // We must make sure the ISO8601 timestamp is full and includes
                // seconds, otherwise Google Earth gets confused.  This is why we
                // convert to a Date and back again.
                Date date = VariableMetadata.dateFormatter.getISODate(tValue);
                timestamp = VariableMetadata.dateFormatter.toDateTimeStringISO(date);
                kml.append("<TimeStamp><when>" + timestamp + "</when></TimeStamp>");
            }
            if (zValue != null && !zValue.equals("") && this.var.getZvalues() != null)
            {
                z = "";
                if (timestamp != null) z += "<br />";
                z += "Elevation: " + zValue + " " + this.var.getZunits();
            }
            kml.append("<name>");
            if (timestamp == null && z == null)
            {
                kml.append("Frame " + frameIndex);
            }
            else
            {
                kml.append("<![CDATA[");
                if (timestamp != null)
                {
                    kml.append("Time: " + timestamp);
                }
                if (z != null)
                {
                    kml.append(z);
                }
                kml.append("]]>");
            }
            kml.append("</name>");
            kml.append("<visibility>1</visibility>");

            kml.append("<Icon><href>" + getPicFileName(frameIndex) + "</href></Icon>");

            kml.append("<LatLonBox id=\"" + frameIndex + "\">");
            kml.append("<west>"  + bbox[0] + "</west>");
            kml.append("<south>" + bbox[1] + "</south>");
            kml.append("<east>"  + bbox[2] + "</east>");
            kml.append("<north>" + bbox[3] + "</north>");
            kml.append("<rotation>0</rotation>");
            kml.append("</LatLonBox>");
            kml.append("</GroundOverlay>");
        }

        // Write the footer of the KML file
        kml.append("</Folder>");
        kml.append("</kml>");
        
        ZipOutputStream zipOut = new ZipOutputStream(out);
        
        // Write the KML file: todo get filename properly
        logger.debug("Writing KML file to KMZ file");
        ZipEntry kmlEntry = new ZipEntry(this.var.getDataset().getId() + "_" +
            this.var.getId() + ".kml");
        kmlEntry.setTime(System.currentTimeMillis());
        zipOut.putNextEntry(kmlEntry);
        zipOut.write(kml.toString().getBytes());
        
        // Now write all the images
        int frameIndex = 0;
        logger.debug("Writing frames to KMZ file");
        for (BufferedImage frame : frames)
        {
            ZipEntry picEntry = new ZipEntry(getPicFileName(frameIndex));
            frameIndex++;
            zipOut.putNextEntry(picEntry);
            ImageIO.write(frame, PICEXT, zipOut);
        }
        
        // Finally, write the colour scale
        // TODO: all dimensions are hard-coded here.  Should be more flexible.
        logger.debug("Constructing colour scale image");
        ZipEntry scaleEntry = new ZipEntry(COLOUR_SCALE_FILENAME);
        zipOut.putNextEntry(scaleEntry);
        BufferedImage colourScale = new BufferedImage(110, 264, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D gfx = colourScale.createGraphics();
        
        // Create the colour scale itself
        Color[] palette = this.getColorPalette();
        for (int i = 5; i < 259; i++)
        {
            gfx.setColor(palette[260 - i]);
            gfx.drawLine(2, i, 25, i);
        }
        logger.debug("Created palette");
        
        // Draw the text items
        gfx.setColor(Color.WHITE);
        // Add the scale values
        double quarter = 0.25 * (this.getScaleMax() - this.getScaleMin());
        String scaleMin          = format(this.getScaleMin());
        String scaleQuarter      = format(this.getScaleMin() + quarter);
        String scaleMid          = format(this.getScaleMin() + 2 * quarter);
        String scaleThreeQuarter = format(this.getScaleMin() + 3 * quarter);
        String scaleMax          = format(this.getScaleMax());
        logger.debug("Writing scale ({}, {}, {}) to colour scale image",
            new Object[]{scaleMin, scaleMid, scaleMax});
        gfx.drawString(scaleMax, 27, 10);
        gfx.drawString(scaleThreeQuarter, 27, 73);
        gfx.drawString(scaleMid, 27, 137);
        gfx.drawString(scaleQuarter, 27, 201);
        gfx.drawString(scaleMin, 27, 264);
        // Add the title as rotated text
        logger.debug("Writing rotated title to colour scale image");
        AffineTransform trans = new AffineTransform();
        trans.setToTranslation(90, 0);
        AffineTransform rot = new AffineTransform();
        rot.setToRotation(Math.PI / 2.0);
        trans.concatenate(rot);
        gfx.setTransform(trans);
        String title = this.var.getTitle();
        if (this.var.getUnits() != null)
        {
            title += " (" + this.var.getUnits() + ")";
        }
        gfx.drawString(title, 5, 0);
        
        // Write the colour scale bar to the KMZ file
        logger.debug("Writing colour scale image to KMZ file");
        ImageIO.write(colourScale, "png", zipOut);
        
        zipOut.close();
    }
    
    /**
     * Formats a number to a limited number of d.p., using scientific notation
     * if necessary
     */
    private static String format(double d)
    {
        // Try decimal format first
        String dec = DECIMAL_FORMATTER.format(d);
        // See if we have at least 3 s.f.:
        if (dec.length() > 4 && dec.charAt(0) == '0' && dec.charAt(2) == '0'
            && dec.charAt(3) == '0' && dec.charAt(4) == '0')
        {
            return SCIENTIFIC_FORMATTER.format(d);
        }
        return dec;
    }
    
    /**
     * @return the name of the picture file with the given index
     */
    private static final String getPicFileName(int frameIndex)
    {
        return PICNAME + frameIndex + "." + PICEXT;
    }
}
