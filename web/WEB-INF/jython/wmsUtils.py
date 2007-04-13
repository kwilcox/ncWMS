# Common utility routines

import urllib, sys

from wmsExceptions import WMSException

if sys.platform.startswith("java"):
    # We're running on Jython.  These Java methods for formatting and
    # parsing ISO8601 dates are more forgiving than their Python counterparts
    # in terms of dates before 1970
    from java.util import Date
    from java.lang import Double
    from ucar.nc2.units import DateFormatter
    df = DateFormatter()
    def secondsToISOString(secondsSince1970):
        return df.toDateTimeStringISO(Date(Double(secondsSince1970 * 1000).longValue()))
    def isoStringToSeconds(isoString):
        """ returns the number of seconds since 1970-01-01 represented by the given ISO8601 string """
        return df.getISODate(isoString).time / 1000.0
    
else:
    # TODO: check for presence of CDAT
    import iso8601
    def secondsToISOString(secondsSince1970):
        return iso8601.tostring(secondsSince1970)
    def isoStringToSeconds(isoString):
        """ returns the number of seconds since 1970-01-01 represented by the given ISO8601 string """
        return iso8601.parse(isoString)

def getWMSVersion():
    """ Returns the version of this WMS server """
    return "1.3.0"

class RequestParser:
    """ Parses request parameters from the URL.  Parameter values are
        case-sensitive, but their names are not.  Translates URL
        escape codes (e.g. %2F) to proper characters (e.g. /). """

    def __init__(self, queryString):
        """ queryString is the unprocessed query string from the URL """
        self._params = {} # Hashtable for query parameters and values
        self.queryString = queryString
        if queryString is not None:
            for kvp in queryString.split("&"):
                keyAndVal = kvp.split("=")
                if len(keyAndVal) == 2:
                    (key, value) = keyAndVal
                    # We always store the key in lower case, escape
                    # the URL % codes and replace "+" with a space
                    self._params[key.lower()] = urllib.unquote_plus(value).strip()

    def getParamValue(self, key, default=None):
        """ Gets the value of the given parameter. If default==None
           and the parameter does not exist, a WMSException is thrown.
           Otherwise, the parameter value is returned, or the default
           value if it does not exist """
        if self._params.has_key(key.lower()):
            return self._params[key.lower()]
        elif default is None:
            raise WMSException("Must provide a " + key.upper() + " argument")
        else:
            return default

def getLayerSeparator():
    """ Returns the string used to delimit dataset and variable names in the
        construction of a layer's name """
    return "/"

