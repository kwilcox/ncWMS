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

package uk.ac.rdg.resc.ncwms.datareader;

import java.io.IOException;
import org.apache.log4j.Logger;
import ucar.nc2.dataset.AxisType;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDatatype;
import uk.ac.rdg.resc.ncwms.metadata.CoordAxis;
import uk.ac.rdg.resc.ncwms.metadata.LUTCoordAxis;

/**
 * DataReader for ROMS data from Damian Smyth (damian.smyth@marine.ie)
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class ROMSDataReader extends USGSDataReader
{
    private static final Logger logger = Logger.getLogger(ROMSDataReader.class);
    
    @Override
    protected boolean includeGrid(GridDatatype grid)
    {
        return grid.getName().equals("temp") || grid.getName().equals("salt") ||
               grid.getName().equals("latent") || grid.getName().equals("sensible") ||
               grid.getName().equals("lwrad") || grid.getName().equals("evaporation");
    }
    
    /**
     * Gets the X axis from the given coordinate system
     */
    @Override
    protected CoordAxis getXAxis(GridCoordSystem coordSys) throws IOException
    {
        return LUTCoordAxis.createAxis("/uk/ac/rdg/resc/ncwms/metadata/LUT_ROMS_1231_721.zip/LUT_i_1231_721.dat", AxisType.GeoX);
    }
    
    /**
     * Gets the Y axis from the given coordinate system
     */
    @Override
    protected CoordAxis getYAxis(GridCoordSystem coordSys) throws IOException
    {
        return LUTCoordAxis.createAxis("/uk/ac/rdg/resc/ncwms/metadata/LUT_ROMS_1231_721.zip/LUT_j_1231_721.dat", AxisType.GeoY);
    }
}
