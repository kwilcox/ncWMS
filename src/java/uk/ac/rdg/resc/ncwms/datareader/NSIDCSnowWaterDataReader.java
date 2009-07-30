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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.ProjectionPoint;
import uk.ac.rdg.resc.ncwms.metadata.Layer;
import uk.ac.rdg.resc.ncwms.metadata.LayerImpl;
import uk.ac.rdg.resc.ncwms.metadata.TimestepInfo;

/**
 * DataReader for NSIDC snow/water data.  This is an example of how to create
 * a DataReader for files that can't be read by the Java-NetCDF libraries.
 * Another alternative method would be to create an IO Service Provider for
 * this dataset.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class NSIDCSnowWaterDataReader extends DataReader
{
    private static final Logger logger = LoggerFactory.getLogger(NSIDCSnowWaterDataReader.class);
    
    /**
     * The number of rows of data in the grid
     */
    private static final int ROWS = 721;
    /**
     * The number of columns of data in the grid
     */
    private static final int COLS = 721;
    /**
     * radius of the earth (km), authalic sphere based on International datum
     */
    private static final double RE_KM = 6371.228;
    /**
     * nominal cell size in kilometers
     */
    private static final double CELL_KM = 25.067525;
    
    /**
     * Reads and returns the metadata for all the variables in the dataset
     * at the given location, which is the location of a NetCDF file, NcML
     * aggregation, or OPeNDAP location ({@literal i.e.} one element resulting from the
     * expansion of a glob aggregation).
     * @param location Full path to the individual file
     * @throws IOException if there was an error reading from the data source
     */
    @Override
    protected void findAndUpdateLayers(String location,
        Map<String, LayerImpl> layers) throws IOException
    {
        // Look for this layer in the list
        LayerImpl layer = layers.get("swe");
        if (layer == null)
        {
            layer = new LayerImpl();
            layer.setId("swe");
            layer.setTitle("snow_water_equivalent");
            layer.setUnits("mm");
            layer.setBbox(new double[]{-180.0, 0.0, 180.0, 90.0});
        }
        
        String filename = new File(location).getName();
        Date timestep;
        try
        {
            // SimpleDateFormats aren't thread safe so we have to keep creating
            // new ones.
            DateFormat df = new SimpleDateFormat("'NL'yyyyMM'.v01.NSIDC8'");
            timestep = df.parse(filename);
        }
        catch(Exception e)
        {
            logger.error("Error parsing filepath " + location, e);
            // TODO: not really an IOException
            throw new IOException("Error parsing filepath " + location);
        }
        layer.addTimestepInfo(new TimestepInfo(new DateTime(timestep), location, 0));
        
        layers.put(layer.getId(), layer);
    }
    
    /**
     * Reads data from a file.  Reads data for a single timestep only.
     * This method knows
     * nothing about aggregation: it simply reads data from the given file.
     * Missing values (e.g. land pixels in oceanography data) will be represented
     * by Float.NaN.
     *
     * @param filename Location of the file, NcML aggregation or OPeNDAP URL
     * @param layer {@link Layer} object representing the variable
     * @param tIndex The index along the time axis (or -1 if there is no time axis)
     * @param zIndex The index along the vertical axis (or -1 if there is no vertical axis)
     * @param pointList The list of real-world x-y points for which we need data
     * @return an array of floating-point data values, one for each point in
     * the {@code pointList}, in the same order.
     * @throws Exception if an error occurs
     */
    public float[] read(String filename, Layer layer, int tIndex, int zIndex, PointList pointList)
        throws Exception
    {
        // Find the file containing the data
        logger.debug("Reading data from " + filename);
        
        // Create an array to hold the data
        float[] picData = new float[pointList.size()];
        Arrays.fill(picData, Float.NaN);
        
        FileInputStream fin = null;
        ByteBuffer data = null;
        // Read the whole of the file into memory
        try
        {
            fin = new FileInputStream(filename);
            data = ByteBuffer.allocate(ROWS * COLS * 2);
            data.order(ByteOrder.LITTLE_ENDIAN);
            // Read the whole of the file into memory
            fin.getChannel().read(data);
        }
        finally
        {
            try { if (fin != null) fin.close(); } catch (IOException ioe) {}
        }
        
        int picIndex = 0;
        for (ProjectionPoint point : pointList.asList())
        {
            LatLonPoint latLon = pointList.getCrsHelper().crsToLatLon(point);
            if (latLon.getLatitude() >= 0.0 && latLon.getLatitude() <= 90.0)
            {
                // Find the index in the source data
                int dataIndex = latLonToIndex(latLon.getLatitude(), latLon.getLongitude());
                // two bytes per pixel
                short val = data.getShort(dataIndex * 2);
                if (val > 0) picData[picIndex] = val;
            }
            picIndex++;
        }
        
        return picData;
    }
    
    /**
     * convert geographic coordinates (spherical earth) to
     *	azimuthal equal area or equal area cylindrical grid coordinates
     *
     *	status = ezlh_convert (grid, lat, lon, &r, &s)
     *
     *	input : grid - projection name "[NSM][lh]"
     *          where l = "low"  = 25km resolution
     *                     h = "high" = 12.5km resolution
     *		lat, lon - geo. coords. (decimal degrees)
     *
     *	output: r, s - column, row coordinates
     *
     *	result: status = 0 indicates normal successful completion
     *			-1 indicates error status (point not on grid)
     */
    private static int latLonToIndex(double lat, double lon)
    {        
        double Rg = RE_KM / CELL_KM;
        
        double r0 = (COLS - 1) / 2.0;
        double s0 = (ROWS - 1) / 2.0;
        
        double phi = Math.toRadians(lat);
        double lam = Math.toRadians(lon);
        
        double rho = 2 * Rg * Math.sin(Math.PI / 4.0 - phi / 2.0);
        
        int col = (int)Math.round(r0 + rho * Math.sin(lam));
        int row = (int)Math.round(s0 + rho * Math.cos(lam));
        
        int index = row * COLS + col;
        return index;
    }
    
}
