<%@include file="xml_header.jsp"%>
<%-- Displays an unexpected exception (i.e. not a WmsException) --%>
<ServiceExceptionReport version="1.3.0"
                        xmlns="http://www.opengis.net/ogc"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.opengis.net/ogc http://schemas.opengis.net/wms/1.3.0/exceptions_1_3_0.xsd">
    <ServiceException>Unexpected error of type ${exception.class.name}: ${exception.message}</ServiceException>
</ServiceExceptionReport>