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

package uk.ac.rdg.resc.ncwms.utils;

import java.text.DateFormat;
import java.util.Date;
import ucar.nc2.units.DateFormatter;
import java.text.SimpleDateFormat;

/**
 * <p>Collection of static utility methods that are useful in the WMS application.</p>
 *
 * <p>Through the taglib definition /WEB-INF/taglib/wmsUtils.tld, these functions
 * are also available as JSP2.0 functions. For example:</p>
 * <code>
 * <%@taglib uri="/WEB-INF/taglib/wmsUtils" prefix="utils"%>
 * The epoch: ${utils:secondsToISO8601(0)}
 * </code>
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class WmsUtils
{
    /**
     * The version of the WMS standard that this server supports
     * @todo Support more versions (e.g. 1.1.1)?
     */
    public static final String VERSION = "1.3.0";

    /**
     * Converts a number of seconds since the epoch into an ISO8601-formatted
     * String.
     */
    public static String secondsToISO8601(double secondsSinceEpoch)
    {
        DateFormatter df = new DateFormatter();
        return df.toDateTimeStringISO(getDate(secondsSinceEpoch));
    }
    
    /**
     * @return a Date object that is equivalent to the given number of seconds
     * since the epoch
     */
    private static Date getDate(double secondsSinceEpoch)
    {
        return new Date(new Double(secondsSinceEpoch * 1000).longValue());
    }
    
    /**
     * Converts an ISO8601-formatted time into a number of seconds since the
     * epoch
     * @todo: shouldn't this throw a parse error?
     */
    public static double iso8061ToSeconds(String iso8601)
    {
        DateFormatter df = new DateFormatter();
        return df.getISODate(iso8601).getTime() / 1000.0;
    }
        
    /**
     * @return the version of WMS that this server supports (equal to the VERSION
     * field but wrapped as a function to support the creation of JSP tags.
     */
    public static final String getVersion()
    {
        return VERSION;
    }
    
    /**
     * @return a heading (e.g. "Oct 2006") for the given date, which is 
     * expressed in seconds since the epoch.  Used by showCalendar.jsp.
     */
    public static String getHeading(double secondsSinceEpoch)
    {
        DateFormat df = new SimpleDateFormat("MMM yyyy");
        return df.format(getDate(secondsSinceEpoch));
    }
    
}
