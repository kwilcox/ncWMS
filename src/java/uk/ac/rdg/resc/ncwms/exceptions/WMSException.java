/*
 * Copyright (c) 2006 The University of Reading
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

package uk.ac.rdg.resc.ncwms.exceptions;

import uk.ac.rdg.resc.ncwms.*;
import uk.ac.rdg.resc.ncwms.ogc.exceptions.ServiceExceptionReport;
import uk.ac.rdg.resc.ncwms.ogc.exceptions.ServiceExceptionType;

/**
 * Exception that is thrown during operation of a WMS in response to an invalid
 * client request (as distinct from an internal server error). It is presented to the 
 * web client as an XML document.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class WMSException extends Exception
{
    private ServiceExceptionReport exReport;
    
    /**
     * Creates a new WMSException
     * @param message A free-form message
     */
    public WMSException(String message)
    {
        this(message, null);
    }
    
    /**
     * Creates a new WMSException: only subclasses can call this method
     * @param message A free-form message
     * @param code One of the official error codes
     */
    protected WMSException(String message, String code)
    {
        this.exReport = new ServiceExceptionReport();
        exReport.setVersion(WMS.VERSION);
        ServiceExceptionType ex = new ServiceExceptionType();
        if (code != null)
        {
            ex.setCode(code);
        }
        ex.setValue(message);
        this.exReport.getServiceException().add(ex);
    }
    
    /**
     * @return the ServiceExceptionReport object, ready for marshalling into
     * XML and returning to the client
     */
    public ServiceExceptionReport getServiceExceptionReport()
    {
        return this.exReport;
    }
    
}