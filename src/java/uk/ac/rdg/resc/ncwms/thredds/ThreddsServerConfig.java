/*
 * Copyright (c) 2010 The University of Reading
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

package uk.ac.rdg.resc.ncwms.thredds;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.joda.time.DateTime;
import ucar.nc2.dataset.NetcdfDataset;
import uk.ac.rdg.resc.ncwms.controller.WmsController;
import uk.ac.rdg.resc.ncwms.controller.AbstractServerConfig;
import uk.ac.rdg.resc.ncwms.wms.Dataset;
import uk.ac.rdg.resc.ncwms.controller.ServerConfig;

/**
 * {@link ServerConfig} for a THREDDS Data Server.  This is injected by Spring
 * into the {@link WmsController} to provide access to data and metadata.
 * @todo There is an inefficiency here: each call to {@link #getDatasetById(java.lang.String)}
 * will generate a new {@link ThreddsDataset} object, which will contain
 * all the child layers.  This means that a lot of unnecessary objects will be
 * created when only a single layer is needed, e.g. for a GetMap operation.
 * An alternative approach would be to override {@link #getLayerByUniqueName(java.lang.String)}
 * and only create the layer in question; but this is a bit complicated because
 * the requested layer might be a dynamically-created "virtual" layer (e.g.
 * a vector layer).
 * @author Jon
 */
public class ThreddsServerConfig extends AbstractServerConfig {

    /**
     * Returns false: THREDDS servers can't produce a capabilities document
     * containing all datasets.
     */
    @Override
    public boolean getAllowsGlobalCapabilities() {
        return false;
    }

    /**
     * Returns null: THREDDS servers can't produce a collection of all the
     * datasets they hold
     */
    @Override
    public Map<String, ? extends Dataset> getAllDatasets() {
        return null;
    }

    /**
     * Returns the current time.  THREDDS servers don't cache their metadata
     * so the datasets could change at any time.  This effectively means that
     * clients should not cache Capabilities documents from THREDDS servers for
     * any "significant" period of time, to prevent inconsistencies between
     * client and server.
     */
    @Override
    public DateTime getLastUpdateTime() {
        return new DateTime();
    }

    @Override
    public Dataset getDatasetById(String datasetId) throws IOException {
       NetcdfDataset nc = this.getNetcdfDataset(datasetId);
       return new ThreddsDataset(datasetId, nc);
    }

    /**
     * Return the NetcdfDataset with the given id, or null if there is no dataset
     * with the given id.
     */
    private NetcdfDataset getNetcdfDataset(String datasetId) {
        throw new UnsupportedOperationException("Implement me!");
    }
    
    
    //// The methods below should be easily populated from existing THREDDS
    //// metadata or the OGCMeta.xml file

    @Override
    public String getTitle() {
        throw new UnsupportedOperationException("Implement me!");
    }

    @Override
    public int getMaxImageWidth() {
        throw new UnsupportedOperationException("Implement me!");
    }

    @Override
    public int getMaxImageHeight() {
        throw new UnsupportedOperationException("Implement me!");
    }

    @Override
    public String getAbstract() {
        throw new UnsupportedOperationException("Implement me!");
    }

    @Override
    public Set<String> getKeywords() {
        throw new UnsupportedOperationException("Implement me!");
    }

    @Override
    public String getServiceProviderUrl() {
        throw new UnsupportedOperationException("Implement me!");
    }

    @Override
    public String getContactName() {
        throw new UnsupportedOperationException("Implement me!");
    }

    @Override
    public String getContactOrganization() {
        throw new UnsupportedOperationException("Implement me!");
    }

    @Override
    public String getContactTelephone() {
        throw new UnsupportedOperationException("Implement me!");
    }

    @Override
    public String getContactEmail() {
        throw new UnsupportedOperationException("Implement me!");
    }

}
