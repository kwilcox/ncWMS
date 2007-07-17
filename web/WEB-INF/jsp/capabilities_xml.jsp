<%@include file="xml_header.jsp"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%> 
<%-- Displays the Capabilities document in XML.  The config object is an
     instance of uk.ac.rdg.resc.ncwms.config.Config --%>
<WMS_Capabilities
        version="1.3.0" <%-- TODO: get this from somewhere central --%>
        <%-- TODO: do UpdateSequence properly --%>
        xmlns="http://www.opengis.net/wms"
        xmlns:xlink="http://www.w3.org/1999/xlink"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.opengis.net/wms http://schemas.opengis.net/wms/1.3.0/capabilities_1_3_0.xsd">
        
    <Service>
        <Name>WMS</Name>
        <Title>${config.server.title}</Title>
        <Abstract>${config.server.abstract}</Abstract>
        <KeywordList>
            <c:forEach var="keyword" items="${config.server.keywords}">
            <Keyword>${keyword}</Keyword>
            </c:forEach>
        </KeywordList>
        <OnlineResource xlink:type="simple" xlink:href="${config.server.url}"/>
        <ContactInformation>
            <ContactPersonPrimary>
                <ContactPerson>${config.contact.name}</ContactPerson>
                <ContactOrganization>${config.contact.org}</ContactOrganization>
            </ContactPersonPrimary>
            <ContactVoiceTelephone>${config.contact.tel}</ContactVoiceTelephone>
            <ContactElectronicMailAddress>${config.contact.email}</ContactElectronicMailAddress>
        </ContactInformation>
        <Fees>none</Fees>
        <AccessConstraints>none</AccessConstraints>
        <%--<LayerLimit>%d</LayerLimit>" % getmap.getLayerLimit())--%>
        <MaxWidth>${config.server.maxImageWidth}</MaxWidth>
        <MaxHeight>${config.server.maxImageHeight}</MaxHeight>
    </Service>
    
    <Capability>
        <Request>
            <c:set var="onlineResource" value="<%=request.getRequestURI()%>"/>
            <GetCapabilities>
                <Format>text/xml</Format>
                <DCPType>
                    <HTTP>
                        <Get>
                            <OnlineResource xlink:type="simple" xlink:href="${onlineResource}"/>
                        </Get>
                    </HTTP>
                </DCPType>
            </GetCapabilities>
        </Request>
        <Exception>
            <Format>XML</Format>
        </Exception>
    </Capability>
    
</WMS_Capabilities>