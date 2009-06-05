/*
 * Copyright (c) 2009 The University of Reading
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

package uk.ac.rdg.resc.ncwms.metadata.lut;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ma2.Array;
import ucar.ma2.IndexIterator;
import ucar.nc2.Dimension;
import ucar.nc2.constants.AxisType;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dataset.CoordinateAxis;
import ucar.nc2.dataset.CoordinateAxis2D;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.TypedDatasetFactory;
import ucar.unidata.geoloc.LatLonRect;

/**
 * Key for an {@link LutCache}.  This contains all the information needed to
 * generate a look-up table.
 * @author Jon
 */
public final class LutCacheKey implements Serializable
{
    private static final long serialVersionUID = 432948123412387L;

    private static final Logger logger = LoggerFactory.getLogger(LutCacheKey.class);

    // These define the source grid: each point on the 2D grid has a longitude
    // and latitude value and a "missing" attribute.  These 2D arrays are flattened
    // into 1D arrays.
    private double[] longitudes;
    private double[] latitudes;
    private boolean[] missing;
    // ni and nj define the dimensions of the 2D grid.  longitudes.length == ni * nj
    private int ni;
    private int nj;

    // These define the look-up table itself: the lat-lon bounding box and the
    // number of points in each direction.
    private double lonMin;
    private double lonMax;
    private double latMin;
    private double latMax;
    private int nLon;
    private int nLat;

    /**
     * Creates and returns a {@link LutCacheKey} from the given variable from
     * a NetCDF dataset.
     * @param var a variable (expresses as a {@link GridDatatype} that contains
     * the coordinate system for which the LUT is to be created.  We need a
     * variable to find the locations of missing values, which are not held
     * within a coordinate system.
     * @param resolutionMultiplier Approximate resolution multiplier for the
     * look-up table.  The LUT will usually have a higher resolution
     * than the original data grid.  If this parameter has a value of 3 (a
     * sensible default) then the final look-up table will have approximately
     * 9 times the number of points in the original grid.
     * @throws IOException if there was an error reading data from the variable
     * @throws IllegalArgumentException if the given {@link GridDatatype} does
     * not use two-dimensional horizontal axes, or if the axes are not longitude
     * and latitude.
     * @todo Do we need to take a parameter that gives the index of the correct
     * z level to use? If the z axis is "upside down" we could end up with a lot
     * of spurious missing values.
     * @todo does the variable need to be "enhanced" to read missing values
     * properly?
     */
    public static LutCacheKey fromVariable(GridDatatype var, int resolutionMultiplier) throws IOException
    {
        logger.debug("Creating LutCacheKey from variable {} with resolution multiplier {}",
            var.getName(), resolutionMultiplier);

        // Check the types of the coordinate axes
        CoordinateAxis xAxis = var.getCoordinateSystem().getXHorizAxis();
        CoordinateAxis yAxis = var.getCoordinateSystem().getYHorizAxis();
        if (!(xAxis instanceof CoordinateAxis2D && yAxis instanceof CoordinateAxis2D))
        {
            throw new IllegalArgumentException("X and Y axes must be two-dimensional");
        }
        if (!(xAxis.getAxisType() == AxisType.Lon && yAxis.getAxisType() == AxisType.Lat))
        {
            throw new IllegalArgumentException("X and Y axes must be longitude and latitude");
        }
        CoordinateAxis2D xAxis2D = (CoordinateAxis2D)xAxis;
        CoordinateAxis2D yAxis2D = (CoordinateAxis2D)yAxis;

        // Create a new key
        LutCacheKey key = new LutCacheKey();

        // Find the number of points in each direction in the lon and lat 2D arrays
        List<Dimension> dimList = xAxis.getDimensions();
        key.nj = dimList.get(0).getLength();
        key.ni = dimList.get(1).getLength();

        // Load the longitude and latitude values
        key.longitudes = xAxis2D.getCoordValues();
        key.latitudes  = yAxis2D.getCoordValues();

        // Find the missing values: read data from the first t and z level
        // TODO will this work in all cases, even 3D/5D data?
        Array arr = var.readDataSlice(0, 0, -1, -1);
        key.missing = new boolean[(int)arr.getSize()];
        int i = 0;
        for (IndexIterator it = arr.getIndexIterator(); it.hasNext(); )
        {
            key.missing[i] = var.isMissingData(it.getDoubleNext());
            i++;
        }

        // Find the latitude-longitude bounding box of the data.  These will
        // define the bounds of the look-up table
        LatLonRect bbox = var.getCoordinateSystem().getLatLonBoundingBox();
        key.lonMin = bbox.getLonMin();
        key.lonMax = bbox.getLonMax();
        key.latMin = bbox.getLatMin();
        key.latMax = bbox.getLatMax();

        // Now calculate the number of points in the LUT along the longitude
        // and latitude directions
        double ratio = (key.lonMax - key.lonMin) / (key.latMax - key.latMin);
        key.nLat = (int) (Math.sqrt((resolutionMultiplier * resolutionMultiplier * key.ni * key.nj) / ratio));
        key.nLon = (int) (ratio * key.nLat);

        // Check that the data in the key is self-consistent
        key.validate();

        logger.debug("Created LutCacheKey from variable {}. Resulting LUT will have {} points",
            var.getName(), key.nLon * key.nLat);
        return key;
    }
    
    /**
     * Checks this object to make sure it appears sane.  The purpose of this method
     * is to help catch errors early, before the key is used.
     * @throws IllegalStateException if there are errors in the key
     */
    private void validate()
    {
        // Check that the arrays are of the right length
        if (this.longitudes.length != this.latitudes.length ||
            this.longitudes.length != this.missing.length   ||
            this.longitudes.length != this.ni * this.nj)
        {
            throw new IllegalStateException("Longitudes, latitudes, missing and "
                + "ni+nj not same length");
        }
        
        if (this.lonMin > this.lonMax || this.latMin > this.latMax)
        {
            String msg = String.format("Invalid bounding box for LUT: %f,%f, %f, %f",
                this.lonMin, this.latMin, this.lonMax, this.latMax);
            throw new IllegalStateException(msg);
        }
        
        if (this.ni <= 0 || this.nj <= 0)
        {
            throw new IllegalStateException("ni and nj must be positive");
        }
        
        if (this.nLon <= 0 || this.nLat <= 0)
        {
            throw new IllegalStateException("nLon and nLat must be positive");
        }
    }

    @Override public int hashCode()
    {
        int result = 17;
        result = 31 * result + Arrays.hashCode(this.longitudes);
        result = 31 * result + Arrays.hashCode(this.latitudes);
        result = 31 * result + Arrays.hashCode(this.missing);
        result = 31 * result + this.ni;
        result = 31 * result + this.nj;
        result = 31 * result + new Double(this.lonMin).hashCode();
        result = 31 * result + new Double(this.lonMax).hashCode();
        result = 31 * result + new Double(this.latMin).hashCode();
        result = 31 * result + new Double(this.latMax).hashCode();
        result = 31 * result + this.nLon;
        result = 31 * result + this.nLat;
        return result;
    }

    @Override public boolean equals(Object obj)
    {
        if (obj == this) return true;
        if (!(obj instanceof LutCacheKey)) return false;
        LutCacheKey other = (LutCacheKey)obj;

        // We do the cheap comparisons first
        return this.ni == other.ni &&
               this.nj == other.nj &&
               this.nLon == other.nLon &&
               this.nLat == other.nLat &&
               this.lonMin == other.lonMin &&
               this.lonMax == other.lonMax &&
               this.latMin == other.latMin &&
               this.latMax == other.latMax &&
               Arrays.equals(this.longitudes, other.longitudes) &&
               Arrays.equals(this.latitudes, other.latitudes) &&
               Arrays.equals(this.missing, other.missing);
    }

    public static void main(String[] args) throws Exception
    {
        String filename = "C:\\Documents and Settings\\Jon\\Desktop\\adriatic.ncml";
        String varId = "temp";
        NetcdfDataset nc = NetcdfDataset.openDataset(filename, true, null);

        GridDataset gd = (GridDataset) TypedDatasetFactory.open(FeatureType.GRID,
                nc, null, null);

        GridDatatype grid = gd.findGridDatatype(varId);

        LutCacheKey key = LutCacheKey.fromVariable(grid, 3);
        System.out.printf("%d,%d : %d,%d%n", key.ni, key.nj, key.nLon, key.nLat);
    }

}
