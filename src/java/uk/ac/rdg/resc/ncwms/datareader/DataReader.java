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

package uk.ac.rdg.resc.ncwms.datareader;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;
import org.apache.log4j.Logger;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.Variable;
import ucar.nc2.dataset.EnhanceScaleMissingImpl;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.VariableDS;
import ucar.nc2.dataset.grid.GeoGrid;
import ucar.nc2.dataset.grid.GridDataset;
import ucar.nc2.units.DateFormatter;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.LatLonPointImpl;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;
import uk.ac.rdg.resc.ncwms.exceptions.WMSExceptionInJava;

/**
 * Provides static methods for reading data and returning as float arrays.
 * Called from nj22dataset.py.  Implemented in Java for efficiency.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class DataReader
{
    private static DateFormatter dateFormatter = new DateFormatter();
    private static final Logger logger = Logger.getLogger(DataReader.class);
    
    /**
     * Read an array of data from a NetCDF file and projects onto a rectangular
     * lat-lon grid.  Reads data for a single time index only.
     *
     * @param location Location of the NetCDF file (full file path, OPeNDAP URL etc)
     * @param varID Unique identifier for the required variable in the file
     * @param tIndex The index along the time axis as found in getmap.py
     * @param zValue The value of elevation as specified by the client
     * @param latValues Array of latitude values
     * @param lonValues Array of longitude values
     * @param fillValue Value to use for missing data
     * @throws WMSExceptionInJava if an error occurs
     */
    public static float[] read(String location, String varID,
        int tIndex, String zValue, float[] latValues, float[] lonValues,
        float fillValue) throws WMSExceptionInJava
    {
        NetcdfDataset nc = null;
        try
        {
            // Get the metadata from the cache
            long start = System.currentTimeMillis();
            VariableMetadata vm = DatasetCache.getVariableMetadata(location, varID);
            if (vm == null)
            {
                throw new WMSExceptionInJava("Could not find variable called "
                    + varID + " in " + location);
            }
            
            Range tRange = new Range(tIndex, tIndex);
            
            // Find the index along the depth axis
            int zIndex = 0; // Default value of z is the first in the axis
            if (zValue != null && !zValue.equals("") && vm.getZvalues() != null)
            {
                zIndex = findZIndex(vm.getZvalues(), zValue);
            }
            Range zRange = new Range(zIndex, zIndex);
            
            if (location.contains("NEMO"))
            {
                // Lousy logic but a quick-fix
                return getNemoData(location, vm, tRange, zRange, latValues, lonValues, fillValue);
            }
            
            EnhancedCoordAxis xAxis = vm.getXaxis();
            EnhancedCoordAxis yAxis = vm.getYaxis();
            
            // Create an array to hold the data
            float[] picData = new float[lonValues.length * latValues.length];
            Arrays.fill(picData, fillValue);
            
            // Find the range of x indices
            int minX = -1;
            int maxX = -1;
            int[] xIndices = new int[lonValues.length];
            for (int i = 0; i < lonValues.length; i++)
            {
                xIndices[i] = xAxis.getIndex(new LatLonPointImpl(0.0, lonValues[i]));
                if (xIndices[i] >= 0)
                {
                    if (minX < 0 || xIndices[i] < minX) minX = xIndices[i];
                    if (maxX < 0 || xIndices[i] > maxX) maxX = xIndices[i];
                }
            }
            // TODO: subsample if we are going to read very many more points
            // than we actually need
            if (minX < 0 || maxX < 0)
            {
                // We haven't found any valid data
                return picData;
            }
            Range xRange = new Range(minX, maxX);
            
            long readMetadata = System.currentTimeMillis();
            logger.debug("Read metadata in {} milliseconds", (readMetadata - start));
            
            // Get the dataset from the cache, without enhancing it
            nc = DatasetCache.getDataset(location);
            long openedDS = System.currentTimeMillis();
            logger.debug("Opened NetcdfDataset in {} milliseconds", (openedDS - readMetadata));            
            GridDataset gd = new GridDataset(nc);
            GeoGrid gg = gd.findGridByName(varID);
            // Get an enhanced version of the variable for fast reading of data
            EnhanceScaleMissingImpl enhanced = getEnhanced(gg);
            
            DataChunk dataChunk;
            // Cycle through the latitude values, extracting a scanline of
            // data each time from minX to maxX
            for (int j = 0; j < latValues.length; j++)
            {
                // Check for out-of-range latitude values
                if (latValues[j] >= -90.0 && latValues[j] <= 90.0)
                {
                    int yIndex = yAxis.getIndex(new LatLonPointImpl(latValues[j], 0.0));
                    if (yIndex >= 0)
                    {
                        Range yRange = new Range(yIndex, yIndex);
                        // Read a chunk of data - values will not be unpacked or
                        // checked for missing values yet
                        GeoGrid subset = gg.subset(tRange, zRange, yRange, xRange);
                        dataChunk = new DataChunk(subset.readYXData(0,0).reduce());
                        // Now copy the scanline's data to the picture array
                        for (int i = 0; i < xIndices.length; i++)
                        {
                            if (xIndices[i] >= 0)
                            {
                                int picIndex = j * lonValues.length + i;
                                float val = dataChunk.getValue(xIndices[i] - minX);
                                // We unpack and check for missing values just for
                                // the points we need to display.
                                float pixel = (float)enhanced.convertScaleOffsetMissing(val);
                                picData[picIndex] = Float.isNaN(pixel) ? fillValue : pixel;
                            }
                        }
                    }
                }
            }
            
            long builtPic = System.currentTimeMillis();
            logger.debug("Built picture in {} milliseconds", (builtPic - readMetadata));
            logger.info("Whole read() operation took {} milliseconds", (builtPic - start));
            
            return picData;
        }
        catch(IOException e)
        {
            logger.error("IOException reading from " + nc.getLocation(), e);
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
    
    /**
     * Finds the index of a certain t value by binary search (the axis may be
     * very long, so a brute-force search is inappropriate)
     * @param tValues Array of doubles representing the t axis values in <b>seconds</b>
     * since the epoch
     * @param tValue Date to search for as an ISO8601-formatted String
     * @return the t index corresponding with the given targetVal
     * @throws InvalidDimensionValueException if targetVal could not be found
     * within tValues
     * @todo almost repeats code in {@link Irregular1DCoordAxis} - refactor?
     */
    public static int findTIndex(double[] tValues, String tValue)
        throws InvalidDimensionValueException
    {
        if (tValue.equals("current"))
        {
            // Return the last index in the array
            return tValues.length - 1;
        }
        Date targetD = dateFormatter.getISODate(tValue);
        if (targetD == null)
        {
            throw new InvalidDimensionValueException("time", tValue);
        }
        double target = targetD.getTime() / 1000.0;
        
        // Check that the point is within range
        if (target < tValues[0] || target > tValues[tValues.length - 1])
        {
            throw new InvalidDimensionValueException("time", tValue);
        }
        
        // do a binary search to find the nearest index
        int low = 0;
        int high = tValues.length - 1;
        while (low <= high)
        {
            int mid = (low + high) >> 1;
            double midVal = tValues[mid];
            if (midVal == target)
            {
                return mid;
            }
            else if (midVal < target)
            {
                low = mid + 1;
            }
            else if (midVal > target)
            {
                high = mid - 1;
            }
        }
        
        // If we've got this far we have to decide between values[low]
        // and values[high]
        if (tValues[low] == target)
        {
            return low;
        }
        else if (tValues[high] == target)
        {
            return high;
        }
        throw new InvalidDimensionValueException("time", tValue);
    }
    
    /**
     * Finds the index of a certain z value by brute-force search.  We can afford
     * to be inefficient here because z axes are not likely to be large.
     * @param zValues Array of values of the z coordinate
     * @param targetVal Value to search for
     * @return the z index corresponding with the given targetVal
     * @throws InvalidDimensionValueException if targetVal could not be found
     * within zValues
     */
    private static int findZIndex(double[] zValues, String targetVal)
        throws InvalidDimensionValueException
    {
        try
        {
            float zVal = Float.parseFloat(targetVal);
            for (int i = 0; i < zValues.length; i++)
            {
                if (Math.abs((zValues[i] - zVal) / zVal) < 1e-5)
                {
                    return i;
                }
            }
            throw new InvalidDimensionValueException("elevation", targetVal);
        }
        catch(NumberFormatException nfe)
        {
            throw new InvalidDimensionValueException("elevation", targetVal);
        }
    }
    
    /**
     * Implemented as a function because for some reason we can't access
     * EnhanceScaleMissingImpl() constructor from Jython.
     */
    public static EnhanceScaleMissingImpl getEnhanced(GeoGrid gg)
    {
        return new EnhanceScaleMissingImpl((VariableDS)gg.getVariable());
    }
    
    private static float[] getNemoData(String location, VariableMetadata vm,
        Range tRange, Range zRange, float[] latValues, float[] lonValues,
        float fillValue) throws IOException, WMSExceptionInJava
    {
        // Create an array to hold the data
        float[] picData = new float[lonValues.length * latValues.length];
        Arrays.fill(picData, fillValue);
        
        EnhancedCoordAxis xAxis = vm.getXaxis();
        EnhancedCoordAxis yAxis = vm.getYaxis();
        
        long start = System.currentTimeMillis();        
        // Maps y indices to scanlines
        Hashtable<Integer, Scanline> scanlines = new Hashtable<Integer, Scanline>();
        // Cycle through each pixel in the picture and work out which
        // x and y index in the source data it corresponds to
        int pixelIndex = 0;
        for (float lat : latValues)
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
        logger.debug("Built scanlines in {} ms", System.currentTimeMillis() - start);
        start = System.currentTimeMillis();
        
        // Now build the picture
        NetcdfDataset nc = DatasetCache.getDataset(location);
        Variable var = nc.findVariable(vm.getId());
        float scaleFactor = var.findAttribute("scale_factor").getNumericValue().floatValue();
        float addOffset = var.findAttribute("add_offset").getNumericValue().floatValue();
        float missingValue = var.findAttribute("missing_value").getNumericValue().floatValue();
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
        
        try
        {
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
                short[] arr = (short[])data.copyTo1DJavaArray();
                
                for (int xIndex : xIndices)
                {
                    for (int p : scanline.getPixelIndices(xIndex))
                    {
                        short val = arr[xIndex - xIndices.firstElement()];
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

            nc.close();
        }
        catch(InvalidRangeException ire)
        {
            logger.error("InvalidRangeException reading from " + nc.getLocation(), ire);
            throw new WMSExceptionInJava("InvalidRangeException: " + ire.getMessage());
        }
        return picData;
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
    
    public static void main(String[] args) throws Exception
    {
        // TODO: check to see if new datasets are reloaded after a clear-out
        // of the cache if force=false and datasets were open when the clear-out
        // was performed.
        String location = "C:\\data\\Waves\\UKWaters_waves_00Z_20061111.nc";
        NetcdfDataset nc = NetcdfDataset.acquireDataset(location, null);
        NetcdfDataset nc2 = NetcdfDataset.acquireDataset(location, null);
        
        /*NetcdfDataset nc = NetcdfDataset.openDataset(location);
        GridDataset gd = new GridDataset(nc);
        GeoGrid gg = gd.findGridByName("wind_speed");
        LatLonRect bbox = gg.getCoordinateSystem().getLatLonBoundingBox();
        System.out.println("" + bbox.getLowerLeftPoint().getLatitude());
        System.out.println("" + bbox.getUpperRightPoint().getLatitude());
        nc.close();*/
        /*String varID = "sst_foundation";
        float fillValue = Float.NaN;
        float[] lonValues = new float[256];
        float[] latValues = new float[256];
        for (int i = 0; i < 256; i++)
        {
            lonValues[i] = i * 90.0f / 256 - 90;
            latValues[i] = i * 90.0f / 256;
        }
        float[] data = read(location, varID, "tvalue", "zvalue", latValues, lonValues, Float.NaN);*/
    }
}
