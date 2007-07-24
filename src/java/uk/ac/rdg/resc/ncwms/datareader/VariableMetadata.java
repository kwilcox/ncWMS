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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import ucar.nc2.units.DateFormatter;
import uk.ac.rdg.resc.ncwms.config.Dataset;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;
import uk.ac.rdg.resc.ncwms.styles.BoxFillStyle;
import uk.ac.rdg.resc.ncwms.styles.VectorStyle;

/**
 * Stores the metadata for a {@link GeoGrid}: saves reading in the metadata every
 * time the dataset is opened (a significant performance hit especially for
 * large NcML aggregations.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class VariableMetadata
{
    public static DateFormatter dateFormatter = new DateFormatter();
    
    private String id;
    private String title;
    private String abstr; // "abstract" is a reserved word
    private String units;
    private String zUnits;
    private double[] zValues;
    private boolean zPositive;
    private double[] bbox; // Bounding box : minx, miny, maxx, maxy
    private double validMin;
    private double validMax;
    private EnhancedCoordAxis xaxis;
    private EnhancedCoordAxis yaxis;
    private Dataset dataset;
    private SortedMap<Date, TimestepInfo> timesteps;
    // Stores the keys of the styles that this variable supports
    private List<String> supportedStyles = new ArrayList<String>();
    
    // If this is a vector quantity, these values will be the northward and
    // eastward components
    private VariableMetadata eastward;
    private VariableMetadata northward;
    
    /** Creates a new instance of VariableMetadata */
    VariableMetadata()
    {
        this.title = null;
        this.abstr = null;
        this.zUnits = null;
        this.zValues = null;
        this.bbox = new double[]{-180.0, -90.0, 180.0, 90.0};
        this.xaxis = null;
        this.yaxis = null;
        this.dataset = null;
        this.timesteps = new TreeMap<Date, TimestepInfo>();
        this.eastward = null;
        this.northward = null;
        this.addStyles(BoxFillStyle.KEYS);
    }
    
    /**
     * Creates a VariableMetadata object that comprises an eastward and 
     * northward component (e.g. for velocities)
     */
    public VariableMetadata(String title, VariableMetadata eastward, VariableMetadata northward)
    {
        // Copy the metadata from the eastward component
        // TODO: check that the two components match
        this.title = title;
        this.abstr = "Automatically-generated vector field, composed of the fields "
            + eastward.title + " and " + northward.title;
        this.zUnits = eastward.zUnits;
        this.zValues = eastward.zValues;
        this.bbox = eastward.bbox;
        this.xaxis = eastward.xaxis;
        this.yaxis = eastward.yaxis;
        this.dataset = eastward.dataset;
        this.units = eastward.units;
        this.timesteps = eastward.timesteps;
        // Vector is the default style, but we can also render as a boxfill
        // (magnitude only)
        this.addStyles(VectorStyle.KEYS);
        this.addStyles(BoxFillStyle.KEYS);
        
        this.eastward = eastward;
        this.northward = northward;
    }
    
    private void addStyles(String[] styles)
    {
        for (String style : styles)
        {
            this.supportedStyles.add(style.trim());
        }
    }
    
    /**
     * @return true if this is a vector quantity (e.g. velocity)
     */
    public boolean isVector()
    {
        return this.getEastwardComponent() != null && this.getNorthwardComponent() != null;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getAbstract()
    {
        return abstr;
    }

    public void setAbstract(String abstr)
    {
        this.abstr = abstr;
    }

    public String getZunits()
    {
        return zUnits;
    }

    public void setZunits(String zUnits)
    {
        this.zUnits = zUnits;
    }

    public double[] getZvalues()
    {
        return zValues;
    }

    public void setZvalues(double[] zValues)
    {
        this.zValues = zValues;
    }

    /**
     * @return array of timestep values in seconds since the epoch
     */
    public synchronized double[] getTvalues()
    {
        double[] tVals = new double[this.timesteps.size()];
        int i = 0;
        for (Date d : this.timesteps.keySet())
        {
            tVals[i] = d.getTime() / 1000.0;
            i++;
        }
        return tVals;
    }

    public double[] getBbox()
    {
        return bbox;
    }

    public void setBbox(double[] bbox)
    {
        this.bbox = bbox;
    }

    public boolean isZpositive()
    {
        return zPositive;
    }

    public void setZpositive(boolean zPositive)
    {
        this.zPositive = zPositive;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public double getValidMin()
    {
        return validMin;
    }

    public void setValidMin(double validMin)
    {
        this.validMin = validMin;
    }

    public double getValidMax()
    {
        return validMax;
    }

    public void setValidMax(double validMax)
    {
        this.validMax = validMax;
    }

    public String getUnits()
    {
        return units;
    }

    public void setUnits(String units)
    {
        this.units = units;
    }

    public EnhancedCoordAxis getXaxis()
    {
        return xaxis;
    }

    public void setXaxis(EnhancedCoordAxis xaxis)
    {
        this.xaxis = xaxis;
    }

    public EnhancedCoordAxis getYaxis()
    {
        return yaxis;
    }

    public void setYaxis(EnhancedCoordAxis yaxis)
    {
        this.yaxis = yaxis;
    }

    public Dataset getDataset()
    {
        return this.dataset;
    }

    public void setDataset(Dataset dataset)
    {
        this.dataset = dataset;
    }
    
    /**
     * Adds a new TimestepInfo to this metadata object.  If a TimestepInfo object
     * already exists for this timestep, the TimestepInfo object with the lower
     * indexInFile value is chosen (this is most likely to be the result of a
     * shorter forecast lead time and therefore more accurate).
     */
    public synchronized void addTimestepInfo(TimestepInfo tInfo)
    {
        TimestepInfo existingTStep = this.timesteps.get(tInfo.timestep);
        if (existingTStep == null || tInfo.indexInFile < existingTStep.indexInFile)
        {
            this.timesteps.put(tInfo.timestep, tInfo);
        }
    }
    
    /**
     * Finds the index of a certain t value by binary search (the axis may be
     * very long, so a brute-force search is inappropriate)
     * @param tValue Date to search for as an ISO8601-formatted String
     * @return the t index corresponding with the given targetVal
     * @throws InvalidDimensionValueException if targetVal could not be found
     * within tValues
     * @todo almost repeats code in {@link Irregular1DCoordAxis} - refactor?
     */
    public int findTIndex(String tValueStr) throws InvalidDimensionValueException
    {
        if (tValueStr.equals("current"))
        {
            // Return the last index in the array
            // TODO: should be the index of the timestep closest to now
            return this.getLastTIndex();
        }
        Date target = dateFormatter.getISODate(tValueStr);
        if (target == null)
        {
            throw new InvalidDimensionValueException("time", tValueStr);
        }
        
        Set<Date> tValues = this.timesteps.keySet();
        Date[] dates = tValues.toArray(new Date[0]);
        
        // Check that the point is within range
        if (target.before(dates[0]) || target.after(dates[dates.length - 1]))
        {
            throw new InvalidDimensionValueException("time", tValueStr);
        }
        
        // do a binary search to find the nearest index
        int low = 0;
        int high = tValues.size() - 1;
        while (low <= high)
        {
            int mid = (low + high) >> 1;
            Date midVal = dates[mid];
            if (midVal.equals(target)) return mid;
            else if (midVal.before(target)) low = mid + 1;
            else high = mid - 1;
        }
        
        // If we've got this far we have to decide between values[low]
        // and values[high]
        if (dates[low].equals(target)) return low;
        else if (dates[high].equals(target)) return high;
        // The given time doesn't match any axis value
        throw new InvalidDimensionValueException("time", tValueStr);
    }
    
    /**
     * @return the TimestepInfo object for the timestep at the given index in the
     * <b>whole dataset</b>.  Returns null if there is no time axis for this dataset.
     */
    public TimestepInfo getTimestepInfo(int datasetTIndex)
    {
        Collection<TimestepInfo> coll = this.timesteps.values();
        TimestepInfo[] tInfos = coll.toArray(new TimestepInfo[0]);
        if (tInfos.length == 0)
        {
            return null;
        }
        return tInfos[datasetTIndex];
    }
    
    /**
     * Finds the index of a certain z value by brute-force search.  We can afford
     * to be inefficient here because z axes are not likely to be large.
     * @param targetVal Value to search for
     * @return the z index corresponding with the given targetVal
     * @throws InvalidDimensionValueException if targetVal could not be found
     * within zValues
     */
    public int findZIndex(String targetVal) throws InvalidDimensionValueException
    {
        try
        {
            float zVal = Float.parseFloat(targetVal);
            for (int i = 0; i < this.zValues.length; i++)
            {
                // The fuzzy comparison fails for zVal == 0.0 so we do a direct
                // comparison too
                if (this.zValues[i] == zVal || Math.abs((this.zValues[i] - zVal) / zVal) < 1e-5)
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
     * Simple class that holds information about which files in an aggregation
     * hold which timesteps for a variable
     */
    public static class TimestepInfo
    {
        private Date timestep;
        private String filename;
        private int indexInFile;

        /**
         * Creates a new TimestepInfo object
         * @param timestep The real date/time of this timestep
         * @param filename The filename containing this timestep
         * @param indexInFile The index of this timestep in the file
         */
        public TimestepInfo(Date timestep, String filename, int indexInFile)
        {
            this.timestep = timestep;
            this.filename = filename;
            this.indexInFile = indexInFile;
        }
        
        public String getFilename()
        {
            return this.filename;
        }
        
        public int getIndexInFile()
        {
            return this.indexInFile;
        }
    }

    public VariableMetadata getEastwardComponent()
    {
        return this.eastward;
    }

    public VariableMetadata getNorthwardComponent()
    {
        return this.northward;
    }

    /**
     * @return List of Strings representing the keys of styles that this
     * variable can be rendered in.
     */
    public List<String> getSupportedStyleKeys()
    {
        return this.supportedStyles;
    }
    
    /**
     * @return the key of the default style for this Variable.  Exactly 
     * equivalent to getSupportedStyleKeys().get(0)
     */
    public String getDefaultStyleKey()
    {
        // Could be an IndexOutOfBoundsException here, but would be a programming
        // error if so
        return this.supportedStyles.get(0);
    }
    
    /**
     * @return true if this Variable can be rendered in the style with the 
     * given name, false otherwise.
     */
    public boolean supportsStyle(String styleName)
    {
        return this.supportedStyles.contains(styleName.trim());
    }
    
    /**
     * @return true if this variable has a depth/elevation axis
     */
    public boolean isZaxisPresent()
    {
        return this.zValues != null && this.zValues.length > 0;
    }
    
    /**
     * @return true if this variable has a time axis
     */
    public boolean isTaxisPresent()
    {
        return this.timesteps != null && this.timesteps.size() > 0;
    }
    
    /**
     * @return the index of the default value on the z axis (i.e. the index of
     * the z value that will be used if the user does not specify an explicit
     * z value in a GetMap request).
     */
    public int getDefaultZIndex()
    {
        return 0;
    }
    
    /**
     * @return the default value of the z axis (i.e. the z value that will be
     * used if the user does not specify an explicit z value in a GetMap request).
     */
    public final double getDefaultZValue()
    {
        return this.zValues[this.getDefaultZIndex()];
    }
    
    /**
     * @return the last index on the t axis
     */
    public int getLastTIndex()
    {
        return this.timesteps.size() - 1;
    }
    
    /**
     * @return the default value of the t axis (i.e. the t value that will be
     * used if the user does not specify an explicit t value in a GetMap request),
     * in seconds since the epoch.  This currently returns the last value along
     * the time axis, but should probably return the value closest to now.
     */
    public final double getDefaultTValue()
    {
        return this.getTvalues()[this.getLastTIndex()];
    }
    
    /**
     * @return a unique identifier string for this VariableMetadata object (used
     * in the display of Layers in a Capabilities document).
     */
    public String getLayerName()
    {
        // NOTE!! The logic of this method must match up with
        // Config.getVariable(layerName)!
        return this.dataset.getId() + "/" + this.id;
    }
    
}
