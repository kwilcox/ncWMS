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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import uk.ac.rdg.resc.ncwms.config.Config;
import uk.ac.rdg.resc.ncwms.config.Dataset;
import uk.ac.rdg.resc.ncwms.datareader.VariableMetadata;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;
import uk.ac.rdg.resc.ncwms.utils.WmsUtils;

/**
 * Controller that handles all requests for non-standard metadata by the
 * Godiva2 site.  Eventually Godiva2 will be changed to accept standard
 * metadata (i.e. fragments of GetCapabilities)... maybe.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */

public class MetadataController
{
    private static final WmsUtils.DayComparator DAY_COMPARATOR =
        new WmsUtils.DayComparator();
    private Config config; // Will be injected by Spring
    
    public ModelAndView handleRequest(HttpServletRequest request,
        HttpServletResponse response) throws Exception
    {
        String item = request.getParameter("item");
        if (item == null)
        {
            throw new WmsException("Must provide an ITEM parameter");
        }
        else if (item.equals("datasets"))
        {
            return this.showDatasets(request, response);
        }
        else if (item.equals("variables"))
        {
            return this.showVariables(request, response);
        }
        else if (item.equals("variableDetails"))
        {
            return this.showVariableDetails(request, response);
        }
        else if (item.equals("calendar"))
        {
            return this.showCalendar(request, response);
        }
        else if (item.equals("timesteps"))
        {
            return this.showTimesteps(request, response);
        }
        else
        {
            throw new WmsException("Invalid value for ITEM parameter");
        }
    }
    
    /**
     * Shows HTML nested divs representing the datasets available from this
     * server.  This HTML is injected directly into the Godiva2 page to form
     * the left-hand accordion-style menu.
     */
    public ModelAndView showDatasets(HttpServletRequest request,
        HttpServletResponse response) throws Exception
    {
        // There could be more than one filter
        String[] filters = request.getParameterValues("filter");
        // Find the list of displayable datasets that match any of the
        // provided filters
        List<Dataset> displayables = new ArrayList<Dataset>();
        for (Dataset ds : this.config.getDatasets().values())
        {
            if (ds.isReady()) // Check that the dataset is loaded properly
            {
                if (filters == null)
                {
                    displayables.add(ds);
                }
                else
                {
                    for (String filter : filters)
                    {
                        if (ds.getId().startsWith(filter))
                        {
                            displayables.add(ds);
                            break;
                        }
                    }
                }
            }
        }
        return new ModelAndView("showDatasets", "datasets", displayables);
    }
    
    /**
     * Shows an HTML table containing a set of variables for the given dataset.
     * This HTML is injected directly into the Godiva2 page to form
     * the left-hand accordion-style menu.
     */
    public ModelAndView showVariables(HttpServletRequest request,
        HttpServletResponse response) throws Exception
    {
        // Find the dataset that the user is interested in
        Dataset ds = this.getDataset(request);
        return new ModelAndView("showVariables", "dataset", ds);
    }
    
    /**
     * @return the Dataset that the user is requesting, throwing an exception if
     * it doesn't exist
     */
    private Dataset getDataset(HttpServletRequest request) throws WmsException
    {
        String dsId = request.getParameter("dataset");
        if (dsId == null)
        {
            throw new WmsException("Must provide a value for the dataset parameter");
        }
        Dataset ds = this.config.getDatasets().get(dsId);
        if (ds == null)
        {
            throw new WmsException("There is no dataset with id " + dsId);
        }
        return ds;
    }
    
    /**
     * Shows an XML document containing the details of the given variable (units,
     * axes, range etc).
     */
    public ModelAndView showVariableDetails(HttpServletRequest request,
        HttpServletResponse response) throws Exception
    {
        VariableMetadata var = this.getVariable(request);
        return new ModelAndView("showVariableDetails", "variable", var);
    }
    
    /**
     * @return the Variable that the user is requesting, throwing a
     * WmsException if it doesn't exist
     */
    private VariableMetadata getVariable(HttpServletRequest request)
        throws WmsException
    {
        Dataset ds = this.getDataset(request);
        String varId = request.getParameter("variable");
        if (varId == null)
        {
            throw new WmsException("Must provide a value for the variable parameter");
        }
        VariableMetadata var = ds.getVariables().get(varId);
        if (var == null)
        {
            throw new WmsException("There is no variable with id " + varId
                + " in the dataset " + ds.getId());
        }
        return var;
    }
    
    /**
     * Shows an XML document (containing an HTML calendar, yuck) that the Godiva2
     * site uses to display the calendar for a particular variable.  The request
     * includes the "focus time", i.e. the time that the user is currently
     * focussed on, in ISO8601 format.
     */
    public ModelAndView showCalendar(HttpServletRequest request,
        HttpServletResponse response) throws Exception
    {
        VariableMetadata vm = getVariable(request);
        String focusTimeIso = request.getParameter("dateTime");
        if (focusTimeIso == null)
        {
            throw new WmsException("Must provide a value for the dateTime parameter");
        }
        // Convert the focus time to seconds since the epoch
        // TODO: this method should throw a ParseException, which we should trap
        double focusTime = WmsUtils.iso8061ToSeconds(focusTimeIso);
        
        // Get the array of time axis values (in seconds since the epoch)
        double[] tVals = vm.getTvalues();
        if (tVals.length == 0) return null; // return no data if no time axis present
        
        // Find the closest time step to the focus time
        // TODO: binary search would be more efficient
        double diff = 1.0e20;
        int nearestIndex = 0;
        for (int i = 0; i < tVals.length; i++)
        {
            double testDiff = Math.abs(tVals[i] - focusTime);
            if (testDiff < diff)
            {
                // Axis is monotonic so we should move closer and closer
                // to the nearest value
                diff = testDiff;
                nearestIndex = i;
            }
            else if (i > 0)
            {
                // We've moved past the closest date
                break;
            }
        }
        
        Map<String, Object> models = new HashMap<String, Object>();
        models.put("nearestIndex", nearestIndex);
        models.put("variable", vm);
        return new ModelAndView("showCalendar", models);
    }
    
    /**
     * Shows an XML document (containing an HTML select box, yuck) that the Godiva2
     * site uses to display the available timesteps for a given date.
     */
    public ModelAndView showTimesteps(HttpServletRequest request,
        HttpServletResponse response) throws Exception
    {
        VariableMetadata vm = getVariable(request);
        String tIndexStr = request.getParameter("tIndex");
        if (tIndexStr == null)
        {
            throw new WmsException("Must provide a value for the tIndex parameter");
        }
        int tIndex = 0;
        try
        {
            tIndex = Integer.parseInt(tIndexStr);
        }
        catch(NumberFormatException nfe)
        {
            throw new WmsException("The value of the tIndex parameter must be a valid integer");
        }
        // Get the array of time axis values (in seconds since the epoch)
        double[] tVals = vm.getTvalues();
        if (tVals.length == 0) return null; // return no data if no time axis present
        
        // List of times (in seconds since the epoch) that fall on this day
        List<Double> timesteps = new ArrayList<Double>();
        // add the reference time
        timesteps.add(tVals[tIndex]);
        
        // Add the rest of the times that fall on this day
        // First count forwards from the reference time...
        for (int i = tIndex + 1; i < tVals.length; i++)
        {
            if (onSameDay(tVals[tIndex], tVals[i])) timesteps.add(tVals[i]);
            else break; // Timesteps are in order so we won't find any more
        }
        // ... now count backwards from the reference time
        for (int i = tIndex - 1; i >= 0; i--)
        {
            if (onSameDay(tVals[tIndex], tVals[i])) timesteps.add(tVals[i]);
            else break; // Timesteps are in order so we won't find any more
        }
        
        return new ModelAndView("showTimesteps", "timesteps", timesteps);
    }
    
    /**
     * @return true if the two given dates (in seconds since the epoch) fall on
     * the same day
     */
    private boolean onSameDay(double s1, double s2)
    {
        return DAY_COMPARATOR.compare(s1, s2) == 0;
    }

    /**
     * Called by Spring to inject the configuration object
     */
    public void setConfig(Config config)
    {
        this.config = config;
    }
    
}
