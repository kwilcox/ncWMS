# Entry point (Jython servlet) for the TileCache

from javax.servlet.http import HttpServlet
from javax.servlet import ServletException

from org.apache.log4j import Logger

from WMS import FakeModPythonRequestObject
from TileCache.Service import handler

# Entry point for the Jython TileCache servlet
class TileCacheServlet (HttpServlet):

    logger = Logger.getLogger("uk.ac.rdg.resc.ncwms.TileCacheServlet")

    def init(self, cfg=None):
        """ This method will be called twice, once with a cfg parameter
            and once without """
        if cfg is None:
            HttpServlet.init(self)
        else:
            HttpServlet.init(self, cfg)
        TileCacheServlet.logger.debug("TileCache Servlet initialized")

    def destroy(self):
        TileCacheServlet.logger.debug("TileCache Servlet destroyed")

    def doGet(self, request, response):
        """ Perform the WMS operation """
        TileCacheServlet.logger.debug("TileCache operation called")
        req = FakeModPythonRequestObject(request, response)
        # Call the handler
        handler(req)

    def doPost(self,request,response):
        raise ServletException("POST method is not supported on this server")

        