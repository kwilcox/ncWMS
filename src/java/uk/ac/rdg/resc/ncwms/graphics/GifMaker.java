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
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import org.apache.log4j.Logger;

/**
 * Creates (possibly animated) GIFs.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class GifMaker extends PicMaker
{
    private static final Logger logger = Logger.getLogger(GifMaker.class);
    
    /** Creates a new instance of GifMaker */
    public GifMaker()
    {
        logger.debug("Created GifMaker");
    }

    public void writeImage(ArrayList<BufferedImage> frames, OutputStream out) throws IOException
    {
        logger.debug("Writing GIF to output stream ...");
        AnimatedGifEncoder e = new AnimatedGifEncoder();
        e.start(out);
        if (frames.size() > 1)
        {
            logger.debug("Animated GIF ({} frames), setting loop count and delay",
                frames.size());
            // this is an animated GIF.  Set to loop infinitely.
            e.setRepeat(0);
            e.setDelay(150); // delay between frames in milliseconds
        }
        boolean firstTime = true;
        for (BufferedImage frame : frames)
        {
            if (firstTime)
            {
                e.setSize(frame.getWidth(), frame.getHeight());
                firstTime = false;
            }
            // Get the indices of each pixel in the image.  We do this after the
            // frames have been created because we might have added a label to
            // the image.
            byte[] indices = ((DataBufferByte)frame.getRaster().getDataBuffer()).getData();
            // The index of the transparent colour is 0 (if transparent=true)
            // TODO - how do we get the RGBPalette cleanly?
            //e.addFrame(this.getRGBPalette(), indices, this.transparent ? 0 : -1);
        }
        e.finish();
        logger.debug("  ... written.");
    }
    
}
