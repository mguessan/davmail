/*
 * Copyright (c) 2008, Michael Stringer
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Growl nor the names of its contributors may be
 *       used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY <copyright holder> ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <copyright holder> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package info.growl;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for sending notifications using Growl.
 *
 * @author Michael Stringer
 * @version 0.1
 */
public final class GrowlUtils {
    private static final boolean GROWL_LOADED;
    private static final Map<String, Growl> instances = new HashMap<String, Growl>();

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("growl");
            loaded = true;
        } catch (UnsatisfiedLinkError ule) {
            // ignore: growl not available
        }

        GROWL_LOADED = loaded;
    }

    /**
     * Utility method - should not be instantiated.
     */
    private GrowlUtils() {
    }

    /**
     * Gets a <code>Growl</code> instance to use for the specified application
     * name. Multiple calls to this method will return the same instance.
     *
     * @param appName The name of the application.
     * @return The <code>Growl</code> instance to use.
     */
    public static Growl getGrowlInstance(String appName) {
        synchronized (instances) {
            Growl instance = instances.get(appName);

            if (instance == null) {
                if (GROWL_LOADED) {
                    instance = new GrowlNative(appName);
                } else {
                    instance = new DummyGrowl();
                }

                instances.put(appName, instance);
            }

            return instance;
        }
    }

    /**
     * Gets whether messages can be sent to Growl. If this returns
     * <code>false</code> then {@link #getGrowlInstance(String)} will return a
     * dummy object.
     *
     * @return <code>true</code> if messages can be sent to Growl.
     */
    public static boolean isGrowlLoaded() {
        return GROWL_LOADED;
    }
}
