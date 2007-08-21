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

package uk.ac.rdg.resc.ncwms.metadata;

import com.sleepycat.persist.model.Persistent;
import java.util.Collections;
import java.util.Vector;
import org.apache.log4j.Logger;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.unidata.geoloc.LatLonPoint;

/**
 * A one-dimensional coordinate axis, whose values are not equally spaced.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
@Persistent
public class Irregular1DCoordAxis extends OneDCoordAxis
{
    private static final Logger logger = Logger.getLogger(Irregular1DCoordAxis.class);
    
    /**
     * Maps axis values to their indices along the axis, sorted in ascending order
     * of value.  This level of
     * elaboration is necessary because some axis values might be NaNs if they
     * are latitude values outside the range -90:90 (possible for some model data).
     * These NaNs are not stored here.
     */
    private Vector<AxisValue> axisVals;
    
    /**
     * Simple class mapping axis values to indices.  Longitudes are always
     * stored in range 0->360
     */
    private static final class AxisValue implements Comparable<AxisValue>
    {
        private double value;
        private int index;
        
        public AxisValue(double value, int index)
        {
            this.value = value;
            this.index = index;
        }
        
        /**
         * Sorts based on the axis value, not the index
         */
        public int compareTo(AxisValue otherVal)
        {
            return Double.compare(this.value, otherVal.value);
        }
    }
    
    /**
     * Creates a new instance of Irregular1DCoordAxis
     */
    public Irregular1DCoordAxis(CoordinateAxis1D axis1D)
    {
        super(axis1D);
        
        // Store the axis values and their indices
        double[] vals = axis1D.getCoordValues();
        this.axisVals = new Vector<AxisValue>(vals.length);
        for (int i = 0; i < vals.length; i++)
        {
            // Might be NaN for a lat axis outside range -90:90
            // (this is less silly than it sounds for model data, which might
            // have latitude values outside this range due to construction of the
            // numerical grid)
            if (!Double.isNaN(vals[i])) 
            {
                this.axisVals.add(new AxisValue(vals[i], i));
            }
        }
        // Now sort the axis values in ascending order
        Collections.sort(this.axisVals);
        
        // Check for wrapping in the longitude direction
        if (this.isLongitude)
        {
            logger.debug("Checking for longitude axis wrapping...");
            double lastVal = this.axisVals.lastElement().value;
            double dx = lastVal - this.axisVals.get(this.axisVals.size() - 2).value;
            // Calculate the position of the imaginary next value along the axis
            double nextVal = lastVal + dx;
            logger.debug("lastVal = {}, nextVal = {}", lastVal, nextVal);
            AxisValue firstVal = this.axisVals.firstElement();
            
            Longitude firstValLon = new Longitude(firstVal.value);
            Longitude lastValLon = new Longitude(lastVal);
            Longitude nextValLon = new Longitude(nextVal);
            
            // We say that the axis wraps if the imaginary next value on the axis
            // is equal to or past the first value on the axis; or if the imaginary
            // next value is closer to the first value than it is to the last value            
            if (firstValLon.isBetween(lastVal, nextVal) ||
                lastValLon.getClockwiseDistanceTo(nextVal) >
                nextValLon.getClockwiseDistanceTo(firstVal.value))
            {
                logger.debug("Axis wraps, creating new point with lon = {}", (firstVal.value + 360));
                // This axis wraps.  Create a new point with the same index as 
                // the first value, but 360 degrees further around the scale
                this.axisVals.add(new AxisValue(firstVal.value + 360, firstVal.index));
            }
        }
        
        logger.debug("Created irregular {} axis",
            (this.isLongitude ? "longitude" : "latitude"));
    }
    
    /**
     * Uses a binary search algorithm to find the index of the point on the axis
     * whose value is closest to the given one.
     * @param point The {@link LatLonPoint}, which will have lon in range
     * [-180,180] and lat in range [-90,90]
     * @return the index that is nearest to this point, or -1 if the point is
     * out of range for the axis
     */
    public int getIndex(LatLonPoint point)
    {
        double target = this.isLongitude ? point.getLongitude() : point.getLatitude();
        logger.debug("Finding index for {} {} ...", this.isLongitude ? "lon" : "lat", target);
        int index = this.findNearest(target);
        if (index < 0 && this.isLongitude && target < 0)
        {
            // We haven't found the point but this could be because this is a
            // longitude axis between 0 and 360 degrees and we're looking for
            // a point at, say, -90 degrees.  Try again.
            index = this.findNearest(target + 360);
        }
        logger.debug("   ...index= {}", index);
        return index;
    }
    
    /**
     * Performs a binary search to find the index of the element of the array
     * whose value is closest to the target
     * @param values The array to search
     * @param target The value to search for
     * @return the index of the element in values whose value is closest to target,
     * or -1 if the target is out of range
     */
    private int findNearest(double target)
    {
        // Check that the point is within range
        // TODO: careful of longitude axis wrapping
        if (target < this.axisVals.firstElement().value || 
            target > this.axisVals.lastElement().value)
        {
            return -1;
        }
        
        // do a binary search to find the nearest index
        int low = 0;
        int high = this.axisVals.size() - 1;
        while (high > low + 1)
        {
            int mid = (low + high) / 2;
            AxisValue midVal = this.axisVals.get(mid);
            if (midVal.value == target) return midVal.index;
            else if (midVal.value < target) low = mid;
            else high = mid;
        }
        
        // If we've got this far then high = low + 1 or high = low
        AxisValue lowVal  = this.axisVals.get(low);
        AxisValue highVal = this.axisVals.get(high);
        return (Math.abs(target - lowVal.value) < 
                Math.abs(target - highVal.value)) ? lowVal.index : highVal.index;
    }
    
}
