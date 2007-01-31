# Entry point (Jython servlet) for the WMS

from javax.servlet.http import HttpServlet
from javax.servlet import ServletException
from java.net import URL
from java.util import Timer, TimerTask

from org.apache.log4j import PropertyConfigurator, Logger

from uk.ac.rdg.resc.ncwms.datareader import DataReader

import time
import ncWMS

class FakeModPythonServerObject:
    """ Class that fakes up the req.server mod_python object """
    def __init__(self, hostname):
        self.server_hostname = hostname

class FakeModPythonRequestObject:
    """ Class that wraps an HttpServletResponse to provide the necessary
        methods and properties of a mod_python request (req) object. 
        This allows us to use identical code for both mod_python and
        Jython servlet implementations """

    def __init__(self, request, response):
        self._response = response
        self.args = request.getQueryString()
        # We would like content_type to be a class property but this is
        # not supported in Python 2.1
        self.content_type = "text/plain"
        reqURL = URL(request.getRequestURL().toString())
        self.server = FakeModPythonServerObject("%s:%d" % (reqURL.getHost(), reqURL.getPort()))
        self.unparsed_uri = str(reqURL.getPath())
        self.headers_out = {} # Dictionary of HTTP headers
        self.headers_set = 0

    def _setHeaders(self):
        """ Sets the content type and other HTTP headers.  Does nothing
            in subsequent invocations """
        if not self.headers_set:
            self.headers_set = 1
            for key in self.headers_out.keys():
                self._response.setHeader(key, self.headers_out[key])
            self._response.setContentType(self.content_type)
 
    def write(self, str):
        """ Writes data to the client."""
        self._setHeaders()
        self._response.getWriter().write(str)

    def getOutputStream(self):
        """ Gets an OutputStream for writing binary data. """
        self._setHeaders()
        return self._response.getOutputStream()

# Entry point for the Jython WMS servlet
class WMS (HttpServlet):

    logger = Logger.getLogger("uk.ac.rdg.resc.ncwms.WMS")
    timer = None
    cacheWiper = None

    def init(self, cfg=None):
        """ This method will be called twice, once with a cfg parameter
            and once without """
        if cfg is None:
            HttpServlet.init(self)
        else:
            HttpServlet.init(self, cfg)
        # These are the things we only do once
        if WMS.timer is None:
            WMS.timer = Timer(1) # timer is a daemon
            # Load the Log4j configuration file
            file = self.getInitParameter("log4j-init-file")
            if file is not None:
                prefix = self.getServletContext().getRealPath("/")
                PropertyConfigurator.configure(prefix + file)
            WMS.logger.debug("Initialized logging system")
            # Initialize the cache of datasets
            DataReader.init()
            WMS.logger.debug("Initialized DatasetCache")
            # Start a timer that will clear the cache at regular intervals
            # so that NcML aggregations are reloaded
            # TODO: get the interval value from a config file
            intervalInMs = int(60 * 1000) # Runs once a minute
            WMS.cacheWiper = CacheWiper()
            WMS.timer.scheduleAtFixedRate(WMS.cacheWiper, intervalInMs, intervalInMs)
            WMS.logger.debug("Initialized NetcdfDatasetCache refresher")
            WMS.logger.debug("ncWMS Servlet initialized")

    def destroy(self):
        DataReader.exit()
        if WMS.timer is not None:
            WMS.timer.cancel()
        WMS.logger.debug("ncWMS Servlet destroyed")

    def doGet(self,request,response):
        """ Perform the WMS operation """
        WMS.logger.debug("GET operation called")
        prefix = self.getServletContext().getRealPath("/")
        ncWMS.doWms(FakeModPythonRequestObject(request, response),
            prefix + "WEB-INF/conf/ncWMS.ini", WMS.cacheWiper.timeLastRan)

    def doPost(self,request,response):
        raise ServletException("POST method is not supported on this server")

class CacheWiper(TimerTask):
    """ Clears the NetcdfDatasetCache at regular intervals """
    def __init__(self):
        self.logger = Logger.getLogger("uk.ac.rdg.resc.ncwms.CacheWiper")
        self.timeLastRan = time.time() # Will be used as UpdateSequence in capabilities doc
    def run(self):
        DataReader.clear()
        self.timeLastRan = time.time()
        self.logger.debug("Cleared cache")
        