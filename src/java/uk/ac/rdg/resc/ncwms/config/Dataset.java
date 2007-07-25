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

package uk.ac.rdg.resc.ncwms.config;

import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import org.apache.log4j.Logger;
import simple.xml.Attribute;
import simple.xml.Root;
import uk.ac.rdg.resc.ncwms.datareader.DataReader;
import uk.ac.rdg.resc.ncwms.datareader.VariableMetadata;

/**
 * A dataset Java bean: contains a number of VariableMetadata objects.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
@Root(name="dataset")
public class Dataset
{
    private static final Logger logger = Logger.getLogger(Dataset.class);
    
    /**
     * The state of a Dataset.
     * TO_BE_LOADED: Dataset is new or has changed and needs to be loaded
     * LOADING: In the process of loading
     * READY: Ready for use
     * UPDATING: A previously-ready dataset is synchronizing with the disk
     * ERROR: An error occurred when loading the dataset.
     */
    public static enum State { TO_BE_LOADED, LOADING, READY, UPDATING, ERROR };
    
    @Attribute(name="id")
    private String id; // Unique ID for this dataset
    @Attribute(name="location")
    private String location; // Location of this dataset (NcML file, OPeNDAP location etc)
    @Attribute(name="queryable", required=false)
    private boolean queryable; // True if we want GetFeatureInfo enabled for this dataset
    @Attribute(name="dataReaderClass", required=false)
    private String dataReaderClass;
    @Attribute(name="title")
    private String title;
    @Attribute(name="updateInterval", required=false)
    private int updateInterval; // The update interval in minutes
    
    // Variables contained in this dataset, keyed by their unique IDs
    private Map<String, VariableMetadata> vars;
    
    private State state;     // State of this dataset
    private Exception err;   // Set if there is an error loading the dataset
    private Date lastUpdate; // Time at which the dataset was last updated
    private Config config;   // The Config object to which this belongs
    
    public Dataset()
    {
        this.vars = new Hashtable<String, VariableMetadata>();
        this.state = State.TO_BE_LOADED;
        this.queryable = true;
        // We'll use a default data reader unless this is overridden in the config file
        this.dataReaderClass = "";
        this.updateInterval = -1; // Means "never update"
        this.lastUpdate = null;
    }

    public String getId()
    {
        return this.id;
    }
    
    public void setId(String id)
    {
        this.id = id.trim();
    }
    
    public String getLocation()
    {
        return location;
    }

    public void setLocation(String location)
    {
        this.location = location.trim();
    }

    public Map<String, VariableMetadata> getVariables()
    {
        return vars;
    }
    
    /**
     * @return true if this dataset is ready for use
     */
    public synchronized boolean isReady()
    {
        return this.state == State.READY || this.state == State.UPDATING;
    }
    
    /**
     * @return true if this dataset can be queried using GetFeatureInfo
     */
    public boolean isQueryable()
    {
        return this.queryable;
    }
    
    public void setQueryable(boolean queryable)
    {
        this.queryable = queryable;
    }
    
    /**
     * @return the human-readable Title of this dataset
     */
    public String getTitle()
    {
        return this.title;
    }
    
    public void setTitle(String title)
    {
        this.title = title;
    }
    
    /**
     * Thread that loads metadata on demand, without waiting for the periodic 
     * reloader ({@link WMSFilter.DatasetReloader})
     */
    private class Refresher extends Thread
    {
        public void run()
        {
            logger.debug("Loading metadata for {}", location);
            boolean loaded = loadMetadata();
            String message = loaded ? "Loaded metadata for {}" :
                "Did not load metadata for {}";
            logger.debug(message, location);
        }
    }
    
    /**
     * @return true if this dataset needs to be reloaded
     */
    private boolean needsRefresh()
    {
        if (this.state == State.LOADING || this.state == State.UPDATING)
        {
            return false;
        }
        else if (this.state == State.ERROR || this.state == State.TO_BE_LOADED
            || this.lastUpdate == null)
        {
            return true;
        }
        else if (this.updateInterval < 0)
        {
            return false; // We never update this dataset
        }
        else
        {
            // State = READY.  Check the age of the metadata
            Calendar cal = Calendar.getInstance();
            cal.setTime(this.lastUpdate);
            cal.add(Calendar.MINUTE, this.updateInterval);
            // Return true if we are after the next scheduled update
            return new Date().after(cal.getTime());
        }
    }
    
    /**
     * (Re)loads the metadata for this Dataset, checking first to see if the
     * metadata need reloading (returning false if not).
     * @return true if the metadata was reloaded, false if not (either because of
     * an error or because the metadata does not need to be reloaded yet).
     */
    public boolean loadMetadata()
    {
        // We must make this part of the method thread-safe because more than
        // one thread might be trying to update the metadata.
        synchronized(this)
        {
            if (this.needsRefresh())
            {
                this.state = this.state == State.READY ? State.UPDATING : State.LOADING;
                this.err = null;
            }
            else
            {
                return false;
            }
        }
        try
        {
            // Get a DataReader object of the correct type
            logger.debug("Getting data reader of type {}", this.dataReaderClass);
            DataReader dr = DataReader.getDataReader(this.dataReaderClass, this.location);
            // Read the metadata
            Map<String, VariableMetadata> vars = dr.getAllVariableMetadata(this.getLocation());
            logger.debug("loaded VariableMetadata");
            // Search for vector quantities (e.g. northward/eastward_sea_water_velocity)
            findVectorQuantities(vars);
            for (VariableMetadata vm : vars.values())
            {
                vm.setDataset(this);
            }
            this.vars = vars;
            this.state = State.READY;
            this.lastUpdate = new Date();
            this.config.setLastUpdateTime(this.lastUpdate);
            return true;
        }
        catch(Exception e)
        {
            logger.error("Error loading metadata for dataset " + this.getId(), e);
            this.err = e;
            this.state = State.ERROR;
            return false;
        }
    }
    
    /**
     * Searches through the collection of VariableMetadata objects, looking for
     * pairs of quantities that represent the components of a vector, e.g.
     * northward/eastward_sea_water_velocity.  Modifies the given Hashtable
     * in-place.
     * @todo Only works for northward/eastward components so far
     */
    private static void findVectorQuantities(Map<String, VariableMetadata> vars)
    {
        // This hashtable will store pairs of components in eastward-northward
        // order, keyed by the standard name for the vector quantity
        Hashtable<String, VariableMetadata[]> components =
            new Hashtable<String, VariableMetadata[]>();
        for (VariableMetadata vm : vars.values())
        {
            if (vm.getTitle().contains("eastward"))
            {
                String vectorKey = vm.getTitle().replaceFirst("eastward_", "");
                // Look to see if we've already found the northward component
                if (!components.containsKey(vectorKey))
                {
                    // We haven't found the northward component yet
                    components.put(vectorKey, new VariableMetadata[2]);
                }
                components.get(vectorKey)[0] = vm;
            }
            else if (vm.getTitle().contains("northward"))
            {
                String vectorKey = vm.getTitle().replaceFirst("northward_", "");
                // Look to see if we've already found the eastward component
                if (!components.containsKey(vectorKey))
                {
                    // We haven't found the eastward component yet
                    components.put(vectorKey, new VariableMetadata[2]);
                }
                components.get(vectorKey)[1] = vm;
            }
            else if (vm.getTitle().contains("_x_"))
            {
                String vectorKey = vm.getTitle().replaceFirst("_x_", "_");
                // Look to see if we've already found the y component
                if (!components.containsKey(vectorKey))
                {
                    // We haven't found the y component yet
                    components.put(vectorKey, new VariableMetadata[2]);
                }
                components.get(vectorKey)[0] = vm;
            }
            else if (vm.getTitle().contains("_y_"))
            {
                String vectorKey = vm.getTitle().replaceFirst("_y_", "_");
                // Look to see if we've already found the x component
                if (!components.containsKey(vectorKey))
                {
                    // We haven't found the x component yet
                    components.put(vectorKey, new VariableMetadata[2]);
                }
                components.get(vectorKey)[1] = vm;
            }
            else if (vm.getTitle().startsWith("x_"))
            {
                String vectorKey = vm.getTitle().replaceFirst("x_", "");
                // Look to see if we've already found the y component
                if (!components.containsKey(vectorKey))
                {
                    // We haven't found the y component yet
                    components.put(vectorKey, new VariableMetadata[2]);
                }
                components.get(vectorKey)[0] = vm;
            }
            else if (vm.getTitle().startsWith("y_"))
            {
                String vectorKey = vm.getTitle().replaceFirst("y_", "");
                // Look to see if we've already found the x component
                if (!components.containsKey(vectorKey))
                {
                    // We haven't found the x component yet
                    components.put(vectorKey, new VariableMetadata[2]);
                }
                components.get(vectorKey)[1] = vm;
            }
        }
        
        // Now add the vector quantities to the collection of VariableMetadata objects
        for (String key : components.keySet())
        {
            VariableMetadata[] comps = components.get(key);
            if (comps[0] != null && comps[1] != null)
            {
                // We've found both components.  Create a new VariableMetadata object
                VariableMetadata vec = new VariableMetadata(key, comps[0], comps[1]);
                // Use the title as the unique ID for this variable
                vec.setId(key);
                vars.put(key, vec);
            }
        }
    }
    
    /**
     * Forces a refresh of the metadata: loads the metadata in a new thread
     */
    public synchronized void forceRefresh()
    {
        this.state = State.TO_BE_LOADED;
        new Refresher().start();
    }
    
    /**
     * @return true if there is an error with this dataset
     */
    public boolean isError()
    {
        return this.state == State.ERROR;
    }
    
    /**
     * If this Dataset has not been loaded correctly, this returns the Exception
     * that was thrown.  If the dataset has no errors, this returns null.
     */
    public Exception getException()
    {
        return this.state == State.ERROR ? this.err : null;
    }
    
    public State getState()
    {
        return this.state;
    }
    
    public String toString()
    {
        return "id: " + this.id + ", location: " + this.location;
    }

    public String getDataReaderClass()
    {
        return dataReaderClass;
    }

    public void setDataReaderClass(String dataReaderClass)
    {
        this.dataReaderClass = dataReaderClass;
    }

    /**
     * @return the update interval for this dataset in minutes
     */
    public int getUpdateInterval()
    {
        return updateInterval;
    }

    /**
     * Sets the update interval for this dataset in minutes
     */
    public void setUpdateInterval(int updateInterval)
    {
        this.updateInterval = updateInterval;
    }

    public void setConfig(Config config)
    {
        this.config = config;
    }
    
    public Date getLastUpdate()
    {
        return this.lastUpdate;
    }
}
