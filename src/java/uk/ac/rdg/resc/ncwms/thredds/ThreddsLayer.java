/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.rdg.resc.ncwms.thredds;

import java.io.IOException;
import java.util.List;
import org.joda.time.DateTime;
import ucar.nc2.dt.GridDatatype;
import uk.ac.rdg.resc.ncwms.cdm.CdmUtils;
import uk.ac.rdg.resc.ncwms.cdm.DataReadingStrategy;
import uk.ac.rdg.resc.ncwms.coords.HorizontalPosition;
import uk.ac.rdg.resc.ncwms.coords.PointList;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;
import uk.ac.rdg.resc.ncwms.util.Range;
import uk.ac.rdg.resc.ncwms.util.Ranges;
import uk.ac.rdg.resc.ncwms.util.WmsUtils;
import uk.ac.rdg.resc.ncwms.wms.AbstractScalarLayer;
import uk.ac.rdg.resc.ncwms.wms.Dataset;

/**
 * Wraps a GridDatatype as a ScalarLayer object
 * @todo Implement more efficient getTimeseries()
 * @author Jon
 */
class ThreddsLayer extends AbstractScalarLayer
{
    private GridDatatype grid;
    private ThreddsDataset dataset;
    private List<DateTime> times;
    private DataReadingStrategy dataReadingStrategy;
    private boolean scaleMissingDeferred;

    public ThreddsLayer(String id) {
        super(id);
    }

    @Override
    public Dataset getDataset() { return this.dataset; }
    public void setDataset(ThreddsDataset dataset) { this.dataset = dataset; }

    public void setGridDatatype(GridDatatype grid) { this.grid = grid; }

    /** Returns true: THREDDS layers are always queryable through GetFeatureInfo */
    @Override
    public boolean isQueryable() { return true; }

    @Override
    public List<DateTime> getTimeValues() {
        return this.times;
    }

    public void setTimeValues(List<DateTime> timeValues) {
        this.times = timeValues;
    }

    public void setDataReadingStrategy(DataReadingStrategy dataReadingStrategy) {
        this.dataReadingStrategy = dataReadingStrategy;
    }

    public void setScaleMissingDeferred(boolean scaleMissingDeferred) {
        this.scaleMissingDeferred = scaleMissingDeferred;
    }

    @Override
    public Float readSinglePoint(DateTime time, double elevation, HorizontalPosition xy)
            throws InvalidDimensionValueException, IOException
    {
        PointList singlePoint = PointList.fromPoint(xy);
        return this.readPointList(time, elevation, singlePoint).get(0);
    }

    @Override
    public List<Float> readPointList(DateTime time, double elevation, PointList pointList)
            throws InvalidDimensionValueException, IOException
    {
        int tIndex = this.findAndCheckTimeIndex(time);
        int zIndex = this.findAndCheckElevationIndex(elevation);
        return CdmUtils.readPointList(
            this.grid,
            this.getHorizontalCoordSys(),
            tIndex,
            zIndex,
            pointList,
            this.dataReadingStrategy,
            this.scaleMissingDeferred
        );
    }

    @Override
    public Range<Float> getApproxValueRange() {
        try {
            // Extract a sample of data from this layer and find the min-max
            // of the sample
            return WmsUtils.estimateValueRange(this);
        } catch (IOException ioe) {
            // Something's gone wrong, so return a sample range
            // TODO: log the error
            return Ranges.newRange(-50.0f, 50.0f);
        }
    }

}
