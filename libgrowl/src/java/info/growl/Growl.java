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

/**
 * Interface for sending notifications to Growl.
 * 
 * @author Michael Stringer
 * @version 0.1
 */
public interface Growl {
    /**
     * Registers this Growl object with the Growl service.
     * 
     * @throws GrowlException
     *                 If an error occurs during the registration.
     */
    public void register() throws GrowlException;

    /**
     * Sets the icon to display for notifications from this <code>Growl</code>.
     * 
     * This <b>must</b> be called before calling {@link #register()}. If the
     * icon is changed after {@link #register()} has been called then another
     * call to {@link #register()} must be made.
     * 
     * @param icon
     *                The icon to display.
     * @throws GrowlException
     *                 If an error occurs while setting the icon.
     */
    public void setIcon(RenderedImage icon) throws GrowlException;

    /**
     * Adds a notification type for this <code>Growl</code>. This <b>must</b>
     * be called before calling {@link #register()}. If the another
     * notification type is added after {@link #register()} has been called then
     * another call to {@link #register()} must be made.
     * 
     * @param name
     *                The name of the notification type. This is what appears in
     *                the Growl settings.
     * @param enabledByDefault
     *                <code>true</code> if this notification type should be
     *                enabled by default. This can be overridden by the user in
     *                the Growl settings.
     */
    public void addNotification(String name, boolean enabledByDefault);

    /**
     * Adds a click callback listener.
     * 
     * @param listener
     *                The callback listener to add.
     */
    public void addCallbackListener(GrowlCallbackListener listener);

    /**
     * Sends a notification to Growl for displaying. This <b>must</b> be called
     * after calling {@link #register()}.
     * 
     * @param name
     *                Name of the notification type that has been registered.
     * @param title
     *                The title of the notification.
     * @param body
     *                The body of the notification.
     * @throws GrowlException
     *                 If the notification could not be sent.
     */
    public void sendNotification(String name, String title, String body)
	    throws GrowlException;

    /**
     * Sends a notification to Growl for displaying. This <b>must</b> be called
     * after calling {@link #register()}.
     * 
     * @param name
     *                Name of the notification type that has been registered.
     * @param title
     *                The title of the notification.
     * @param body
     *                The body of the notification.
     * @param icon
     *                The icon to display with the notification.
     * @throws GrowlException
     *                 If the notification could not be sent.
     */
    public void sendNotification(String name, String title, String body,
	    RenderedImage icon) throws GrowlException;

    /**
     * Sends a notification to Growl for displaying. This <b>must</b> be called
     * after calling {@link #register()}.
     * 
     * @param name
     *                Name of the notification type that has been registered.
     * @param title
     *                The title of the notification.
     * @param body
     *                The body of the notification.
     * @param callbackContext
     *                A unique ID that will be sent to any registered
     *                {@link GrowlCallbackListener}s. If this is
     *                <code>null</code> then clicks will be ignored.
     * @throws GrowlException
     *                 If the notification could not be sent.
     */
    public void sendNotification(String name, String title, String body,
	    String callbackContext) throws GrowlException;

    /**
     * Sends a notification to Growl for displaying. This <b>must</b> be called
     * after calling {@link #register()}.
     * 
     * @param name
     *                Name of the notification type that has been registered.
     * @param title
     *                The title of the notification.
     * @param body
     *                The body of the notification.
     * @param callbackContext
     *                A unique ID that will be sent to any registered
     *                {@link GrowlCallbackListener}s. If this is
     *                <code>null</code> then clicks will be ignored.
     * @param icon
     *                The icon to display with the notification.
     * @throws GrowlException
     *                 If the notification could not be sent.
     */
    public void sendNotification(String name, String title, String body,
	    String callbackContext, RenderedImage icon) throws GrowlException;
}
