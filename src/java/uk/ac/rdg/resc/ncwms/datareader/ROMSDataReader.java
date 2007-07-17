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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import org.apache.log4j.Logger;
import org.apache.oro.io.GlobFilenameFilter;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.Variable;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.grid.GeoGrid;
import ucar.nc2.dataset.grid.GridCoordSys;
import ucar.nc2.dataset.grid.GridDataset;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.LatLonPointImpl;
import ucar.unidata.geoloc.LatLonRect;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;

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
    
    /**
     * Reads and returns the metadata for all the variables in the dataset
     * at the given location.
     * @param location The location of the NetCDF dataset
     * @return Hashtable of variable IDs mapped to {@link VariableMetadata} objects
     * @throws IOException if there was an error reading from the data source
     */
    public Hashtable<String, VariableMetadata> getVariableMetadata(String location)
        throws IOException
    {
        logger.debug("Reading metadata for dataset {}", location);
        Hashtable<String, VariableMetadata> vars = new Hashtable<String, VariableMetadata>();
        
        String[] filenames = null;
        File locFile = null; // Only used if not an opendap location
        if (this.isOpendapLocation(location))
        {
            filenames = new String[]{location};
        }
        else
        {
            // The location might be a glob expression, in which case the last part
            // of the location path will be the filter expression
            locFile = new File(location);
            GlobFilenameFilter filter = new GlobFilenameFilter(locFile.getName());
            // Loop over all the files that match the glob pattern
            filenames = locFile.getParentFile().list(filter);
        }
        
        NetcdfDataset nc = null;
        try
        {
            for (String filepath : filenames)
            {
                if (!isOpendapLocation(location))
                {
                    // Prepend the full path
                    filepath = new File(locFile.getParentFile(), filepath).getPath();
                }
                logger.debug("Reading metadata from file {}", filepath);
                // We use openDataset() rather than acquiring from cache
                // because we need to enhance the dataset
                nc = NetcdfDataset.openDataset(filepath, true, null);
                GridDataset gd = new GridDataset(nc);
                for (Iterator it = gd.getGrids().iterator(); it.hasNext(); )
                {
                    GeoGrid gg = (GeoGrid)it.next();
                    if (!gg.getName().equals("temp") && !gg.getName().equals("salt")
                        && !gg.getName().equals("latent") && !gg.getName().equals("sensible")
                        && !gg.getName().equals("lwrad") && !gg.getName().equals("evaporation"))
                    {
                        // Only display temperature data for the moment
                        continue;
                    }
                    GridCoordSys coordSys = gg.getCoordinateSystem();
                    // Get the VM object from the hashtable
                    VariableMetadata vm = vars.get(gg.getName());
                    if (vm == null)
                    {
                        // This is the first time we've seen this variable in 
                        // this list of files
                        logger.debug("Creating new VariableMetadata object for {}", gg.getName());
                        vm = new VariableMetadata();
                        vm.setId(gg.getName());
                        vm.setTitle(getStandardName(gg.getVariable().getOriginalVariable()));
                        vm.setAbstract(gg.getDescription());
                        vm.setUnits(gg.getUnitsString());
                        vm.setXaxis(LUTCoordAxis.createAxis("/uk/ac/rdg/resc/ncwms/datareader/LUT_ROMS_1231_721.zip/LUT_i_1231_721.dat"));
                        vm.setYaxis(LUTCoordAxis.createAxis("/uk/ac/rdg/resc/ncwms/datareader/LUT_ROMS_1231_721.zip/LUT_j_1231_721.dat"));

                        if (coordSys.hasVerticalAxis())
                        {
                            CoordinateAxis1D zAxis = coordSys.getVerticalAxis();
                            vm.setZunits(zAxis.getUnitsString());
                            double[] zVals = zAxis.getCoordValues();
                            vm.setZpositive(false);
                            vm.setZvalues(zVals);
                        }

                        // Set the bounding box
                        // TODO: should take into account the cell bounds
                        LatLonRect latLonRect = coordSys.getLatLonBoundingBox();
                        LatLonPoint lowerLeft = latLonRect.getLowerLeftPoint();
                        LatLonPoint upperRight = latLonRect.getUpperRightPoint();
                        double minLon = lowerLeft.getLongitude();
                        double maxLon = upperRight.getLongitude();
                        double minLat = lowerLeft.getLatitude();
                        double maxLat = upperRight.getLatitude();
                        if (latLonRect.crossDateline())
                        {
                            minLon = -180.0;
                            maxLon = 180.0;
                        }
                        vm.setBbox(new double[]{minLon, minLat, maxLon, maxLat});

                        vm.setValidMin(gg.getVariable().getValidMin());
                        vm.setValidMax(gg.getVariable().getValidMax());
                        // Add this to the Hashtable
                        vars.put(vm.getId(), vm);
                    }
                    
                    // Now add the timestep information to the VM object
                    Date[] tVals = this.getTimesteps(nc, gg);
                    for (int i = 0; i < tVals.length; i++)
                    {
                        VariableMetadata.TimestepInfo tInfo = new
                            VariableMetadata.TimestepInfo(tVals[i], filepath, i);
                        vm.addTimestepInfo(tInfo);
                    }
                    
                    // (TODO: for safety we could check that the other axes
                    // match, just in case we're accidentally trying to
                    // aggregate two separate datasets)
                }
                nc.close();
            }
            return vars;
        }
        finally
        {
            if (nc != null)
            {
                try
                {
                    nc.close();
                }
                catch (IOException ex)
                {
                    logger.error("IOException closing " + nc.getLocation(), ex);
                }
            }
        }
    }
    
}
