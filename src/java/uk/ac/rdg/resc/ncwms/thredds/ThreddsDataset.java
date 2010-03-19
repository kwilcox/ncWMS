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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joda.time.DateTime;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.GridDatatype;
import uk.ac.rdg.resc.ncwms.cdm.AbstractScalarLayerBuilder;
import uk.ac.rdg.resc.ncwms.cdm.CdmUtils;
import uk.ac.rdg.resc.ncwms.cdm.DataReadingStrategy;
import uk.ac.rdg.resc.ncwms.cdm.LayerBuilder;
import uk.ac.rdg.resc.ncwms.util.WmsUtils;
import uk.ac.rdg.resc.ncwms.wms.Dataset;
import uk.ac.rdg.resc.ncwms.wms.Layer;
import uk.ac.rdg.resc.ncwms.wms.VectorLayer;

/**
 * A {@link Dataset} that provides access to layers read from {@link NetcdfDataset}
 * objects.
 * @author Jon
 */
class ThreddsDataset implements Dataset
{

    private final String id;
    private final String title;
    private final Map<String, ThreddsLayer> scalarLayers = new LinkedHashMap<String, ThreddsLayer>();
    private final Map<String, VectorLayer> vectorLayers = new LinkedHashMap<String, VectorLayer>();

    /** LayerBuilder used to create ThreddsLayers in CdmUtils.findAndUpdateLayers */
    private static final LayerBuilder<ThreddsLayer> THREDDS_LAYER_BUILDER = new AbstractScalarLayerBuilder<ThreddsLayer>()
    {
        @Override public ThreddsLayer newLayer(String id) {
            return new ThreddsLayer(id);
        }
        @Override public void setTimeValues(ThreddsLayer layer, List<DateTime> times) {
            layer.setTimeValues(times);
        }
        @Override public void setGridDatatype(ThreddsLayer layer, GridDatatype grid) {
            layer.setGridDatatype(grid);
        }
    };

    /**
     * Creates a new ThreddsDataset with the given id from the given NetcdfDataset
     * @throws IOException if there was an i/o error extracting a GridDataset
     * from the given NetcdfDataset
     */
    public ThreddsDataset(String id, NetcdfDataset nc) throws IOException
    {
        this.id = id;
        this.title = nc.getTitle();

        // Get the most appropriate data-reading strategy for this dataset
        DataReadingStrategy drStrategy = CdmUtils.getOptimumDataReadingStrategy(nc);

        // Find out if the NetcdfDataset has deferred the application of
        // scale-offset-missing (meaning that we have to apply these attributes
        // when the data are read)
        boolean scaleMissingDeferred = CdmUtils.isScaleMissingDeferred(nc);

        // Now load the scalar layers
        GridDataset gd = CdmUtils.getGridDataset(nc);
        CdmUtils.findAndUpdateLayers(gd, THREDDS_LAYER_BUILDER, this.scalarLayers);
        // Set the dataset property of each layer
        for (ThreddsLayer layer : this.scalarLayers.values())
        {
            layer.setDataReadingStrategy(drStrategy);
            layer.setScaleMissingDeferred(scaleMissingDeferred);
            layer.setDataset(this);
        }

        // Find the vector quantities
        Collection<VectorLayer> vectorLayersColl = WmsUtils.findVectorLayers(this.scalarLayers.values());
        // Add the vector quantities to the map of layers
        for (VectorLayer vecLayer : vectorLayersColl)
        {
            this.vectorLayers.put(vecLayer.getId(), vecLayer);
        }
    }

    /** Returns the ID of this dataset, unique on the server. */
    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    /**
     * Returns the current time, since datasets could change at any time without
     * our knowledge.
     * @see ThreddsServerConfig#getLastUpdateTime()
     */
    @Override
    public DateTime getLastUpdateTime() {
        return new DateTime();
    }

    /**
     * Gets the {@link Layer} with the given {@link Layer#getId() id}.  The id
     * is unique within the dataset, not necessarily on the whole server.
     * @return The layer with the given id, or null if there is no layer with
     * the given id.
     * @todo repetitive of code in ncwms.config.Dataset: any way to refactor?
     */
    @Override
    public Layer getLayerById(String layerId)
    {
        Layer layer = this.scalarLayers.get(layerId);
        if (layer == null) layer = this.vectorLayers.get(layerId);
        return layer;
    }

    /**
     * @todo repetitive of code in ncwms.config.Dataset: any way to refactor?
     */
    @Override
    public Set<Layer> getLayers()
    {
        Set<Layer> layerSet = new LinkedHashSet<Layer>();
        layerSet.addAll(this.scalarLayers.values());
        layerSet.addAll(this.vectorLayers.values());
        return layerSet;
    }

    /** Returns an empty string */
    @Override
    public String getCopyrightStatement() {
        return "";
    }

    /** Returns an empty string */
    @Override
    public String getMoreInfoUrl() {
        return "";
    }

    @Override
    public boolean isReady() { return true; }

    @Override
    public boolean isLoading() { return false; }

    @Override
    public boolean isError() { return false; }

    @Override
    public Exception getException() { return null; }

    @Override
    public boolean isDisabled() { return false; }

}
