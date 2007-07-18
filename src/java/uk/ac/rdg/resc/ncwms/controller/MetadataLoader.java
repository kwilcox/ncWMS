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

package uk.ac.rdg.resc.ncwms.controller;

import java.util.Timer;
import java.util.TimerTask;
import org.apache.log4j.Logger;
import ucar.nc2.dataset.NetcdfDatasetCache;
import uk.ac.rdg.resc.ncwms.config.Config;
import uk.ac.rdg.resc.ncwms.config.Dataset;

/**
 * Class that handles the periodic reloading of metadata (manages calls to
 * Dataset.loadMetadata()).  Initialized by the Spring framework.  No other
 * object in the ncWMS system needs to interact directly with this object.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class MetadataLoader
{
    private static final Logger logger = Logger.getLogger(MetadataLoader.class);
    
    private Config config;
    private Timer timer;
    
    /**
     * Called by the Spring framework to initialize this object
     */
    public void init()
    {
        // Initialize the cache of NetcdfDatasets
        NetcdfDatasetCache.init();
        logger.debug("NetcdfDatasetCache initialized");
        
        // Now start the regular TimerTask that periodically checks to see if
        // the datasets need reloading
        this.timer = new Timer("Dataset reloader", true);
        // TODO: read this interval from an init-param
        int intervalMs = 60 * 1000; // Check every minute
        this.timer.schedule(new DatasetReloader(), 0, intervalMs);
        logger.debug("Started periodic reloading of datasets");
    }
    
    /**
     * Task that runs periodically, refreshing the metadata catalogue
     */
    private class DatasetReloader extends TimerTask
    {
        public void run()
        {
            logger.debug("Reloading metadata...");
            for (Dataset ds : config.getDatasets().values())
            {
                // loadMetadata() includes a check to see if the metadata need
                // reloading
                ds.loadMetadata();
            }
            logger.debug("... Metadata reloaded");
        }
    }
    
    /**
     * Called by the Spring framework to clean up this object
     */
    public void close()
    {
        if (this.timer != null) this.timer.cancel();
        this.config = null;
        NetcdfDatasetCache.exit();
        logger.debug("Cleaned up MetadataLoader");
    }

    /**
     * Called by the Spring framework to inject the configuration object
     */
    public void setConfig(Config config)
    {
        this.config = config;
    }
    
}
