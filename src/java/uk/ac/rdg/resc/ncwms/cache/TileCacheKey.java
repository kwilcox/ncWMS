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

package uk.ac.rdg.resc.ncwms.cache;

import java.io.File;
import java.io.Serializable;
import uk.ac.rdg.resc.ncwms.datareader.HorizontalGrid;
import uk.ac.rdg.resc.ncwms.metadata.Layer;

/**
 * Key that is used to identify a particular data array (tile) in a
 * {@link TileCache}.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class TileCacheKey implements Serializable
{
    private String layerId;    // The unique identifier of this layer
    private String crsCode;    // The CRS code used for this tile
    private double[] bbox;     // Bounding box as [minX, minY, maxX, maxY]
    private int width;         // Width of tile in pixels
    private int height;        // Height of tile in pixels
    private String filepath;   // Full path to the file containing the data
    private long lastModified; // The time at which the file was last modified
                               // (used to check for changes to the file)
    private long fileSize;     // The size of the file in bytes
                               // (used to check for changes to the file)
    private int tIndex;        // The t index of this tile in the file
    private int zIndex;        // The z index of this tile in the file
    
    /**
     * Creates a key for the storing and locating of data arrays in a TileCache.
     * @throws IllegalArgumentException if the given filepath does not represent
     * a valid file on the server
     */
    public TileCacheKey(String filepath, Layer layer, HorizontalGrid grid,
        int tIndex, int zIndex)
    {
        this.layerId = layer.getId();
        this.crsCode = grid.getCrsCode();
        this.bbox = grid.getBbox();
        this.width = grid.getWidth();
        this.height = grid.getHeight();
        File f = new File(filepath);
        if (!f.exists() || !f.isFile())
        {
            throw new IllegalArgumentException(filepath + " is not a valid file on this server");
        }
        this.filepath = filepath;
        this.lastModified = f.lastModified();
        this.fileSize = f.length();
        this.tIndex = tIndex;
        this.zIndex = zIndex;
    }
    
}
