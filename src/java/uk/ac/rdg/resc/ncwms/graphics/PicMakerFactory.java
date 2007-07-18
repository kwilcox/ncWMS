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

package uk.ac.rdg.resc.ncwms.graphics;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class that is used to create instances of PicMaker.
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public class PicMakerFactory
{
    /**
     * Maps image formats (MIME types) to zero-argument constructors of PicMakers.
     * This is populated from setPicMakerClasses()
     */
    private Map<String, Constructor<? extends PicMaker>> picMakers =
        new HashMap<String, Constructor<? extends PicMaker>>();
    
    /**
     * @return the supported image MIME types as a Set of Strings
     */
    public Set<String> getSupportedImageFormats()
    {
        return this.picMakers.keySet();
    }
    
    /**
     * Called by the Spring framework to set the supported image MIME types 
     * and their associated Classes.  Checks that the supplied classes have
     * zero-argument constructors.
     * @throws Exception if a supplied class does not extend PicMaker, if a
     * supplied class does not have a zero-argument constructor or if the
     * zero-argument constructor is not accessible
     */
    public void setPicMakerClasses(Map<String, Class<? extends PicMaker>> picMakerClasses)
        throws Exception
    {
        for (String mimeType : picMakerClasses.keySet())
        {
            Class clazz = picMakerClasses.get(mimeType);
            if (!PicMaker.class.isAssignableFrom(clazz))
            {
                throw new Exception(clazz.getName() + " does not inherit from "
                    + PicMaker.class.getName());
            }
            this.picMakers.put(mimeType, clazz.getConstructor());
        }
    }
    
}
