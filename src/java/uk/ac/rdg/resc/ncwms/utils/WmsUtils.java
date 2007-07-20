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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

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
        return dateToISO8601(getDate(secondsSinceEpoch));
    }

    /**
     * Converts a Date object into an ISO8601-formatted String.
     */
    public static String dateToISO8601(Date date)
    {
        return new DateFormatter().toDateTimeStringISO(date);
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
    public static String getCalendarHeading(double secondsSinceEpoch)
    {
        DateFormat df = new SimpleDateFormat("MMM yyyy");
        return df.format(getDate(secondsSinceEpoch));
    }
    
    /**
     * @return an ISO8601-formatted date that is exactly one year earlier than
     * the given date, in seconds since the epoch.
     */
    public static String getYearBefore(double secondsSinceEpoch)
    {
        Calendar cal = getCalendar(secondsSinceEpoch);
        cal.add(Calendar.YEAR, -1);
        return dateToISO8601(cal.getTime());
    }
    
    /**
     * @return a new Calendar object, set to the given time (in seconds since
     * the epoch).
     */
    private static Calendar getCalendar(double secondsSinceEpoch)
    {
        Date date = getDate(secondsSinceEpoch);
        Calendar cal = Calendar.getInstance();
        // Must set the time zone to avoid problems with daylight saving
        cal.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        cal.setTime(date);
        return cal;
    }
    
    /**
     * @return an ISO8601-formatted date that is exactly one year later than
     * the given date, in seconds since the epoch.
     */
    public static String getYearAfter(double secondsSinceEpoch)
    {
        Calendar cal = getCalendar(secondsSinceEpoch);
        cal.add(Calendar.YEAR, 1);
        return dateToISO8601(cal.getTime());
    }
    
    /**
     * @return an ISO8601-formatted date that is exactly one month earlier than
     * the given date, in seconds since the epoch.
     */
    public static String getMonthBefore(double secondsSinceEpoch)
    {
        Calendar cal = getCalendar(secondsSinceEpoch);
        cal.add(Calendar.MONTH, -1);
        return dateToISO8601(cal.getTime());
    }
    
    /**
     * @return an ISO8601-formatted date that is exactly one month later than
     * the given date, in seconds since the epoch.
     */
    public static String getMonthAfter(double secondsSinceEpoch)
    {
        Calendar cal = getCalendar(secondsSinceEpoch);
        cal.add(Calendar.MONTH, 1);
        return dateToISO8601(cal.getTime());
    }
    
    /**
     * <p>@return a calendar representation of the month that contains the date
     * represented by the given number of seconds since the epoch.  Each item
     * in the returned List represents a week in the calendar (starting on a
     * Monday).  Each week is represented by an array of 7 integers, giving the
     * day number on each day of that week.  If a day does not belong in the
     * calendar for that month, its value will be -1.</p>
     *
     * <p>For example, for a date in March 2007, this will return a calendar
     * of the form:<p>
     * <table border="1">
     * <tr><td>-1</td><td>-1</td><td>-1</td><td>1</td><td>2</td><td>3</td><td>4</td></tr>
     * <tr><td>5</td><td>6</td><td>7</td><td>8</td><td>9</td><td>10</td><td>11</td></tr>
     * <tr><td>12</td><td>13</td><td>14</td><td>15</td><td>16</td><td>17</td><td>18</td></tr>
     * <tr><td>19</td><td>20</td><td>21</td><td>22</td><td>23</td><td>24</td><td>25</td></tr>
     * <tr><td>26</td><td>27</td><td>28</td><td>29</td><td>30</td><td>31</td><td>-1</td></tr>
     * </table>
     */
    public static List<int[]> getWeeksOfMonth(double secondsSinceEpoch)
    {
        List<int[]> weeks = new ArrayList<int[]>();
        Calendar cal = getCalendar(secondsSinceEpoch);
        
        // Find the first Monday of the month
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK_IN_MONTH, 1);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        
        // If this isn't the first day of the month then we have a partial first week
        if (day != 1) day -= 7; // Start with the week before the first Monday
        
        // Construct the weeks
        int lastDayOfMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        while (day < lastDayOfMonth)
        {
            int[] week = new int[7];
            int firstDayNextWeek = day + week.length;
            for (int i = 0; i < week.length; i++)
            {
                if (day >= 1 && day <= lastDayOfMonth) week[i] = day;
                else week[i] = -1; // indicates a day that's not present in the current month
                day++;
            }
            weeks.add(week);
        }
        
        return weeks;
    }
    
}
