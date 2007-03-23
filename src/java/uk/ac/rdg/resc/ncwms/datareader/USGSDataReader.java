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
import uk.ac.rdg.resc.ncwms.exceptions.WMSExceptionInJava;

/**
 * DataReader for Rich Signell's example data
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class USGSDataReader extends DefaultDataReader
{
    private static final Logger logger = Logger.getLogger(USGSDataReader.class);
    
    /**
     * Reads an array of data from a NetCDF file and projects onto a rectangular
     * lat-lon grid.  Reads data for a single time index only.
     *
     * @param location Location of the NetCDF dataset (full file path, OPeNDAP URL etc)
     * @param vm {@link VariableMetadata} object representing the variable
     * @param tIndex The index along the time axis as found in getmap.py
     * @param zIndex The index along the vertical axis (or 0 if there is no vertical axis)
     * @param latValues Array of latitude values
     * @param lonValues Array of longitude values
     * @param fillValue Value to use for missing data
     * @throws WMSExceptionInJava if an error occurs
     */
    public float[] read(String location, VariableMetadata vm,
        int tIndex, int zIndex, float[] latValues, float[] lonValues,
        float fillValue) throws WMSExceptionInJava
    {
        // TODO: allow for aggregated dataset - see DefaultDataReader.
        // This assumes that the whole dataset is one NetCDF or NcML file
        NetcdfDataset nc = null;
        try
        {
            // Get the metadata from the cache
            long start = System.currentTimeMillis();
            
            Range tRange = new Range(tIndex, tIndex);
            Range zRange = new Range(zIndex, zIndex);
            
            // Create an array to hold the data
            float[] picData = new float[lonValues.length * latValues.length];
            Arrays.fill(picData, fillValue);

            EnhancedCoordAxis xAxis = vm.getXaxis();
            EnhancedCoordAxis yAxis = vm.getYaxis();
      
            // Maps y indices to scanlines
            Hashtable<Integer, Scanline> scanlines = new Hashtable<Integer, Scanline>();
            // Cycle through each pixel in the picture and work out which
            // x and y index in the source data it corresponds to
            int pixelIndex = 0;
            for (float lat : latValues)
            {
                if (lat >= -90.0f && lat <= 90.0f)
                {
                    for (float lon : lonValues)
                    {
                        LatLonPoint latLon = new LatLonPointImpl(lat, lon);
                        // Translate lat-lon to projection coordinates
                        int xCoord = xAxis.getIndex(latLon);
                        int yCoord = yAxis.getIndex(latLon);
                        //logger.debug("Lon: {}, Lat: {}, x: {}, y: {}", new Object[]{lon, lat, xCoord, yCoord});
                        if (xCoord >= 0 && yCoord >= 0)
                        {
                            // Get the scanline for this y index
                            Scanline scanline = scanlines.get(yCoord);
                            if (scanline == null)
                            {
                                scanline = new Scanline();
                                scanlines.put(yCoord, scanline);
                            }
                            scanline.put(xCoord, pixelIndex);
                        }
                        pixelIndex++;
                    }
                }
            }
            logger.debug("Built scanlines in {} ms", System.currentTimeMillis() - start);
            start = System.currentTimeMillis();

            // Now build the picture
            nc = getDataset(location);
            Variable var = nc.findVariable(vm.getId());
            
            float scaleFactor = 1.0f;
            float addOffset = 0.0f;
            if (var.findAttribute("scale_factor") != null)
            {
                scaleFactor = var.findAttribute("scale_factor").getNumericValue().floatValue();
            }
            if (var.findAttribute("add_offset") != null)
            {
                addOffset = var.findAttribute("add_offset").getNumericValue().floatValue();
            }
            float missingValue = Float.NaN;
            if (var.findAttribute("missing_value") != null)
            {
                missingValue = var.findAttribute("missing_value").getNumericValue().floatValue();
            }
            logger.debug("Scale factor: {}, add offset: {}", scaleFactor, addOffset);

            int yAxisIndex = 1;
            int xAxisIndex = 2;
            Vector<Range> ranges = new Vector<Range>();
            ranges.add(tRange);
            // TODO: logic is fragile here
            if (var.getRank() == 4)
            {
                ranges.add(zRange);
                yAxisIndex = 2;
                xAxisIndex = 3;
            }
        
            // Add dummy ranges for x and y
            ranges.add(new Range(0,0));
            ranges.add(new Range(0,0));

            // Iterate through the scanlines, the order doesn't matter
            for (int yIndex : scanlines.keySet())
            {
                Scanline scanline = scanlines.get(yIndex);
                ranges.setElementAt(new Range(yIndex, yIndex), yAxisIndex);
                Vector<Integer> xIndices = scanline.getSortedXIndices();
                Range xRange = new Range(xIndices.firstElement(), xIndices.lastElement());
                ranges.setElementAt(xRange, xAxisIndex);

                // Read the scanline from the disk, from the first to the last x index
                Array data = var.read(ranges);
                Object arrObj = data.copyTo1DJavaArray();
                
                for (int xIndex : xIndices)
                {
                    for (int p : scanline.getPixelIndices(xIndex))
                    {
                        float val;
                        if (arrObj instanceof float[])
                        {
                            val = ((float[])arrObj)[xIndex - xIndices.firstElement()];
                        }
                        else
                        {
                            // We assume this is an array of shorts
                            val = ((short[])arrObj)[xIndex - xIndices.firstElement()];
                        }
                        // The missing value is calculated based on the compressed,
                        // not the uncompressed, data, despite the fact that it's
                        // recorded as a float
                        if (val != missingValue)
                        {
                            float realVal = addOffset + val * scaleFactor;
                            if (realVal >= vm.getValidMin() && realVal <= vm.getValidMax())
                            {
                                picData[p] = realVal;
                            }
                        }
                    }
                }
            }
            logger.debug("Read data in {} ms", System.currentTimeMillis() - start);
            return picData;
        }
        catch(IOException e)
        {
            if (nc != null)
            {
                logger.error("IOException reading from " + nc.getLocation(), e);
            }
            else
            {
                logger.error("IOException", e);
            }
            throw new WMSExceptionInJava("IOException: " + e.getMessage());
        }
        catch(InvalidRangeException ire)
        {
            logger.error("InvalidRangeException reading from " + nc.getLocation(), ire);
            throw new WMSExceptionInJava("InvalidRangeException: " + ire.getMessage());
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
    
    private static class Scanline
    {
        // Maps x indices to a collection of pixel indices
        //                  x          pixels
        private Hashtable<Integer, Vector<Integer>> xIndices;
        
        public Scanline()
        {
            this.xIndices = new Hashtable<Integer, Vector<Integer>>();
        }
        
        /**
         * @param xIndex The x index of the point in the source data
         * @param pixelIndex The index of the corresponding point in the picture
         */
        public void put(int xIndex, int pixelIndex)
        {
            Vector<Integer> pixelIndices = this.xIndices.get(xIndex);
            if (pixelIndices == null)
            {
                pixelIndices = new Vector<Integer>();
                this.xIndices.put(xIndex, pixelIndices);
            }
            pixelIndices.add(pixelIndex);
        }
        
        /**
         * @return a Vector of all the x indices in this scanline, sorted in
         * ascending order
         */
        public Vector<Integer> getSortedXIndices()
        {
            Vector<Integer> v = new Vector<Integer>(this.xIndices.keySet());
            Collections.sort(v);
            return v;
        }
        
        /**
         * @return a Vector of all the pixel indices that correspond to the
         * given x index, or null if the x index does not exist in the scanline
         */
        public Vector<Integer> getPixelIndices(int xIndex)
        {
            return this.xIndices.get(xIndex);
        }
    }
    
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
                    if (!gg.getName().equals("temp") && !gg.getName().equals("shflux")
                        && !gg.getName().equals("ssflux") && !gg.getName().equals("latent")
                        && !gg.getName().equals("sensible") && !gg.getName().equals("lwrad")
                        && !gg.getName().equals("swrad") && !gg.getName().equals("zeta"))
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
                        vm.setXaxis(LUTCoordAxis.createAxis("/uk/ac/rdg/resc/ncwms/datareader/LUT_USGS_501_351.zip/LUT_USGS_i_501_351.dat"));
                        vm.setYaxis(LUTCoordAxis.createAxis("/uk/ac/rdg/resc/ncwms/datareader/LUT_USGS_501_351.zip/LUT_USGS_j_501_351.dat"));

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
                    Date[] tVals = this.getTimesteps(gg);
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
