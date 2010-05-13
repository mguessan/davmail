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

import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Growl notification implementation. This uses JNI to send messages to Growl.
 * 
 * @author Michael Stringer
 * @version 0.1
 */
class GrowlNative implements Growl {
    private String appName;
    private List<NotificationType> notifications;
    private List<GrowlCallbackListener> callbackListeners;
    private byte[] imageData;

    private native void sendNotification(String appName, String name,
	    String title, String message, String callbackContext, byte[] icon);

    private native void registerApp(String appName, byte[] image,
	    List<NotificationType> notifications);

    /**
     * Creates a new <code>GrowlNative</code> instance for the specified
     * application name.
     * 
     * @param appName
     *                The name of the application sending notifications.
     */
    GrowlNative(String appName) {
	notifications = new ArrayList<NotificationType>();
	callbackListeners = new ArrayList<GrowlCallbackListener>();
	this.appName = appName;
    }

    void fireCallbacks(String callbackContext) {
	for (GrowlCallbackListener listener : callbackListeners) {
	    listener.notificationWasClicked(callbackContext);
	}
    }

    /**
     * {@inheritDoc}
     */
    public void register() throws GrowlException {
	registerApp(appName, imageData, notifications);
    }

    /**
     * {@inheritDoc}
     */
    public void addNotification(String name, boolean enabledByDefault) {
	notifications.add(new NotificationType(name, enabledByDefault));
    }

    /**
     * {@inheritDoc}
     */
    public void addCallbackListener(GrowlCallbackListener listener) {
	callbackListeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    public void setIcon(RenderedImage icon) throws GrowlException {
	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ImageIO.write(icon, "png", baos);

	    imageData = baos.toByteArray();
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }

    /**
     * {@inheritDoc}
     */
    public void sendNotification(String name, String title, String body)
	    throws GrowlException {
	if (!notifications.contains(new NotificationType(name, false))) {
	    System.out.println("contains: " + notifications);
	    throw new GrowlException("Unregistered notification name [" + name
		    + "]");
	}
	sendNotification(appName, name, title, body, null, null);
    }

    /**
     * {@inheritDoc}
     */
    public void sendNotification(String name, String title, String body,
	    RenderedImage icon) throws GrowlException {
	if (!notifications.contains(new NotificationType(name, false))) {
	    System.out.println("contains: " + notifications);
	    throw new GrowlException("Unregistered notification name [" + name
		    + "]");
	}

	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ImageIO.write(icon, "png", baos);

	    byte[] image = baos.toByteArray();

	    sendNotification(appName, name, title, body, null, image);
	} catch (IOException ioe) {
	    throw new GrowlException("Failed to convert Image", ioe);
	}
    }

    /**
     * {@inheritDoc}
     */
    public void sendNotification(String name, String title, String body,
	    String callbackContext) throws GrowlException {
	if (!notifications.contains(new NotificationType(name, false))) {
	    System.out.println("contains: " + notifications);
	    throw new GrowlException("Unregistered notification name [" + name
		    + "]");
	}

	sendNotification(appName, name, title, body, callbackContext, null);
    }

    /**
     * {@inheritDoc}
     */
    public void sendNotification(String name, String title, String body,
	    String callbackContext, RenderedImage icon) throws GrowlException {
	if (!notifications.contains(new NotificationType(name, false))) {
	    System.out.println("contains: " + notifications);
	    throw new GrowlException("Unregistered notification name [" + name
		    + "]");
	}

	try {
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ImageIO.write(icon, "png", baos);

	    byte[] image = baos.toByteArray();

	    sendNotification(appName, name, title, body, callbackContext, image);
	} catch (IOException ioe) {
	    throw new GrowlException("Failed to convert Image", ioe);
	}
    }

    private class NotificationType {
	private String name;
	private boolean enabledByDefault;

	public NotificationType(String name, boolean enabledByDefault) {
	    this.name = name;
	    this.enabledByDefault = enabledByDefault;
	}

	public String getName() {
	    return name;
	}

	public boolean isEnabledByDefault() {
	    return enabledByDefault;
	}

	public boolean equals(Object other) {
	    if (!(other instanceof NotificationType)) {
		return false;
	    }

	    NotificationType otherType = (NotificationType) other;

	    return name.equals(otherType.name);
	}
    }
}
