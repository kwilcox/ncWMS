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
import java.util.Hashtable;
import org.apache.log4j.Logger;
import uk.ac.rdg.resc.ncwms.exceptions.WMSExceptionInJava;

/**
 * Abstract superclass for classes that read data and metadata from NetCDF datasets.
 * Called from nj22dataset.py.
 * @author jdb
 */
public abstract class DataReader
{
    private static final Logger logger = Logger.getLogger(DataReader.class);
    
    /**
     * Maps class names to DataReader objects.  Only one DataReader object of
     * each class will ever be created.
     */
    private static Hashtable<String, DataReader> readers = new Hashtable<String, DataReader>();
    
    /**
     * This class can only be instantiated through getDataReader()
     */
    protected DataReader()
    {
    }
    
    /**
     * Gets a DataReader object.  <b>Only one</b> object of each class will be
     * created (hence methods have to be thread-safe).
     * @param className Name of the class to generate
     * @param the location of the dataset: used to detect OPeNDAP URLs
     * @return a DataReader object of the given class, or {@link DefaultDataReader}
     * or {@link OpendapDataReader} (depending on whether the location starts with
     * "http://" or "dods://") if <code>className</code> is null or the empty string
     * @throws a {@link WMSExceptionInJava} if the DataReader could not be created
     */
    public static DataReader getDataReader(String className, String location)
        throws WMSExceptionInJava
    {
        String clazz = DefaultDataReader.class.getName();
        if (isOpendapLocation(location))
        {
            clazz = OpendapDataReader.class.getName();
        }
        try
        {
            if (className != null && !className.trim().equals(""))
            {
                clazz = className;
            }
            if (!readers.containsKey(clazz))
            {
                // Create the DataReader object
                Object drObj = Class.forName(clazz).newInstance();
                // this will throw a ClassCastException if drObj is not a DataReader
                readers.put(clazz, (DataReader)drObj);
            }
            return readers.get(clazz);
        }
        catch(ClassNotFoundException cnfe)
        {
            logger.error("Class not found: " + clazz, cnfe);
            throw new WMSExceptionInJava("Internal error: class " + clazz +
                " not found");
        }
        catch(InstantiationException ie)
        {
            logger.error("Instantiation error for class: " + clazz, ie);
            throw new WMSExceptionInJava("Internal error: class " + clazz +
                " could not be instantiated");
        }
        catch(IllegalAccessException iae)
        {
            logger.error("Illegal access error for class: " + clazz, iae);
            throw new WMSExceptionInJava("Internal error: constructor for " + clazz +
                " could not be accessed");
        }
    }
    
    protected static boolean isOpendapLocation(String location)
    {
        return location.startsWith("http://") || location.startsWith("dods://")
            || location.startsWith("thredds");
    }
    
    /**
     * Reads an array of data from a NetCDF file and projects onto a rectangular
     * lat-lon grid.  Reads data for a single time index only.
     * @param location Location of the dataset
     * @param vm {@link VariableMetadata} object representing the variable
     * @param tIndex The index along the time axis as found in getmap.py
     * @param zValue The value of elevation as specified by the client
     * @param latValues Array of latitude values
     * @param lonValues Array of longitude values
     * @param fillValue Value to use for missing data
     * @return array of data values
     * @throws WMSExceptionInJava if an error occurs
     */
    public float[] read(String location, VariableMetadata vm,
        int tIndex, String zValue, float[] latValues, float[] lonValues,
        float fillValue) throws WMSExceptionInJava
    {
        try
        {
            // Find the index along the depth axis
            int zIndex = 0; // Default value of z is the first in the axis
            if (zValue != null && !zValue.equals("") && vm.getZvalues() != null)
            {
                zIndex = vm.findZIndex(zValue);
            }
            return this.read(location, vm, tIndex, zIndex, latValues, lonValues, fillValue);
        }
        catch(WMSExceptionInJava wmeij)
        {
            throw wmeij;
        }
        catch(Exception e)
        {
            logger.error(e.getMessage(), e);
            throw new WMSExceptionInJava(e.getMessage());
        }
    }
    
    /**
     * Reads an array of data from a NetCDF file and projects onto a rectangular
     * lat-lon grid.  Reads data for a single time index only.
     * @param location Location of the dataset
     * @param vm {@link VariableMetadata} object representing the variable
     * @param tIndex The index along the time axis as found in getmap.py
     * @param zIndex The index along the vertical axis (or 0 if there is no vertical axis)
     * @param latValues Array of latitude values
     * @param lonValues Array of longitude values
     * @param fillValue Value to use for missing data
     * @throws WMSExceptionInJava if an error occurs
     */
    protected abstract float[] read(String location, VariableMetadata vm,
        int tIndex, int zIndex, float[] latValues, float[] lonValues,
        float fillValue) throws WMSExceptionInJava;
    
    /**
     * Reads and returns the metadata for all the variables in the dataset
     * at the given location.
     * @param location Full path to the dataset
     * @return Hashtable of variable IDs mapped to {@link VariableMetadata} objects
     * @throws IOException if there was an error reading from the data source
     */
    public abstract Hashtable<String, VariableMetadata> getVariableMetadata(String location)
        throws IOException;

}
