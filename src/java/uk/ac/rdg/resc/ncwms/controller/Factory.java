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

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidFormatException;
import uk.ac.rdg.resc.ncwms.graphics.PicMaker;

/**
 * A Factory stores a Map of keys to class names, and can create objects of these
 * classes using the createObject(key) method.  All the classes must inherit from
 * a common superclass, T.  Factories are used to create various
 * things in the WMS that are keyed by Strings (e.g. PicMakers are keyed by
 * MIME types, Styles are keyed by the style name, Grids are keyed by the
 * CRS string, etc.)
 *
 * @author Jon Blower
 * $Revision$
 * $Date$
 * $Log$
 */
public final class Factory<T>
{
    private static final Logger logger = Logger.getLogger(Factory.class);
    
    /**
     * Maps String keys to zero-argument Constructors of the correct class
     */
    private Map<String, Constructor<? extends T>> constructors =
        new HashMap<String, Constructor<? extends T>>();
    
    /**
     * The superclass of objects that createObject will return
     */
    private Class<T> superClass;
    
    /**
     * Creates a new Factory that can be used to create objects that extend
     * the given superclass
     */
    public Factory(Class<T> superClass)
    {
        this.superClass = superClass;
    }
    
    /**
     * @return the supported keys as a Set of Strings
     */
    public Set<String> getKeys()
    {
        return this.constructors.keySet();
    }
    
    /**
     * Creates a new object from the class given by the given key.
     * @param key The key
     * @return An object of type T, or null if the given key is not recognized.
     * @throws an {@link Exception} if the object could not be created (an
     * internal error, unlikely to happen).
     */
    public T createObject(String key) throws Exception
    {
        Constructor<? extends T> constructor = this.constructors.get(key);
        if (constructor == null) return null;
        // It's very unlikely that newInstance() or cast() will throw an error
        // because we've checked in setClasses() that the class is of the correct
        // type
        return constructor.newInstance();
    }
    
    /**
     * Sets the keys and their associated Classes.  Checks that the supplied
     * classes have zero-argument constructors.
     * @throws Exception if a supplied class does not extend the superclass, if a
     * supplied class does not have a zero-argument constructor or if the
     * zero-argument constructor is not accessible
     */
    public void setClasses(Map<String, Class<? extends T>> classes) throws Exception
    {
        for (String key : classes.keySet())
        {
            Class<? extends T> clazz = classes.get(key);
            // Spring2.0 doesn't do the necessary checks on the type of the
            // classes so we make doubly sure here that the class really does
            // inherit from T.  This works on the principle of catching errors early
            // and avoids the possibility of later ClassCastExceptions
            if (!this.superClass.isAssignableFrom(clazz))
            {
                throw new Exception(clazz.getName() + " does not inherit from "
                    + this.superClass.getName());
            }
            Constructor<? extends T> constructor = clazz.getConstructor();
            this.constructors.put(key.trim(), constructor);
        }
    }
    
}
