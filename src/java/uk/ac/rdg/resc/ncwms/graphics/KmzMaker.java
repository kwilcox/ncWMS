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
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import uk.ac.rdg.resc.ncwms.datareader.VariableMetadata;

/**
 * Creates KMZ files for importing into Google Earth.  We inherit from GifMaker
 * because this contains code that can handle animations.  However, we actually
 * make PNG files!  Read the code to understand how this works... TODO: refactor
 * this more sensibly.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class KmzMaker extends GifMaker
{
    private StringBuffer kml; // The KML that accompanies the images
    
    private static final String PICNAME = "frame";
    private static final String PICEXT  = "png";
    
    /** Creates a new instance of KmzMaker */
    public KmzMaker()
    {
        this.kml = new StringBuffer();
    }

    public void addFrame(float[] data, float[] bbox, String zValue,
        String tValue, boolean isAnimation) throws IOException
    {
        // GifMaker.addFrame() creates the Image if it can, otherwise it stores the data
        // Each individual frame is separate and not part of an animation: setting
        // isAnimation = false prevents the time from being added to the frames
        // as labels
        super.addFrame(data, bbox, zValue, tValue, false);
        int frameIndex = this.getNumFrames() - 1;
        
        if (frameIndex == 0)
        {
            // This is the first frame.  Add the KML header and folder metadata
            this.kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            this.kml.append(System.getProperty("line.separator"));
            this.kml.append("<kml xmlns=\"http://earth.google.com/kml/2.0\">");
            this.kml.append("<Folder>");
            this.kml.append("<visibility>1</visibility>");
            this.kml.append("<name>" + this.var.getDatasetId() + ", " +
                this.var.getId() + "</name>");
            this.kml.append("<description>" + this.var.getDatasetTitle() + ", "
                + this.var.getTitle() + ": " + this.var.getAbstract() +
                "</description>");
        }
        
        this.kml.append("<GroundOverlay>");
        String timestamp = null;
        String z = null;
        if (tValue != null && !tValue.equals(""))
        {
            // We must make sure the ISO8601 timestamp is full and includes
            // seconds, otherwise Google Earth gets confused.  This is why we
            // convert to a Date and back again.
            Date date = VariableMetadata.dateFormatter.getISODate(tValue);
            timestamp = VariableMetadata.dateFormatter.toDateTimeStringISO(date);
            this.kml.append("<TimeStamp><when>" + timestamp + "</when></TimeStamp>");
        }
        if (zValue != null && !zValue.equals("") && this.var.getZvalues() != null)
        {
            z = "";
            if (timestamp != null) z += "<br />";
            z += "Elevation: " + zValue + " " + this.var.getZunits();
        }
        this.kml.append("<name>");
        if (timestamp == null && z == null)
        {
            this.kml.append("Frame " + frameIndex);
        }
        else
        {
            this.kml.append("<![CDATA[");
            if (timestamp != null)
            {
                this.kml.append("Time: " + timestamp);
            }
            if (z != null)
            {
                this.kml.append(z);
            }
            this.kml.append("]]>");
        }
        this.kml.append("</name>");
        this.kml.append("<visibility>1</visibility>");
        
        this.kml.append("<Icon><href>" + getPicFileName(frameIndex) + "</href></Icon>");
        
        this.kml.append("<LatLonBox id=\"" + frameIndex + "\">");
        this.kml.append("<west>"  + bbox[0] + "</west>");
        this.kml.append("<south>" + bbox[1] + "</south>");
        this.kml.append("<east>"  + bbox[2] + "</east>");
        this.kml.append("<north>" + bbox[3] + "</north>");
        this.kml.append("<rotation>0</rotation>");
        this.kml.append("</LatLonBox>");
        this.kml.append("</GroundOverlay>");
    }

    public void writeImage(OutputStream out) throws IOException
    {
        // Write the footer of the KML file
        this.kml.append("</Folder>");
        this.kml.append("</kml>");
        
        ZipOutputStream zipOut = new ZipOutputStream(out);
        
        // Write the KML file: todo get filename properly
        ZipEntry kmlEntry = new ZipEntry(this.var.getDatasetId() + "_" +
            this.var.getId() + ".kml");
        kmlEntry.setTime(System.currentTimeMillis());
        zipOut.putNextEntry(kmlEntry);
        zipOut.write(this.kml.toString().getBytes());
        
        // Now write all the images
        this.createAllFrames();
        int frameIndex = 0;
        for (BufferedImage frame : this.frames)
        {
            ZipEntry picEntry = new ZipEntry(getPicFileName(frameIndex));
            frameIndex++;
            zipOut.putNextEntry(picEntry);
            ImageIO.write(frame, PICEXT, zipOut);
        }
        
        zipOut.close();
    }
    
    /**
     * @return the name of the picture file with the given index
     */
    private static final String getPicFileName(int frameIndex)
    {
        return PICNAME + frameIndex + "." + PICEXT;
    }
    
}