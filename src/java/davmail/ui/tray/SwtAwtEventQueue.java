/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package davmail.ui.tray;

import org.apache.log4j.Logger;
import org.eclipse.swt.internal.gtk.OS;

import java.awt.*;
import java.util.EmptyStackException;

/**
 * Custom AWT event queue to trap X errors and avoid application crash.
 */
public class SwtAwtEventQueue extends EventQueue {
    protected static final Logger LOGGER = Logger.getLogger(SwtAwtEventQueue.class);

    /**
     * Register SWT GDK error handler
     */
    public static void registerErrorHandler() {
        OS.gdk_error_trap_push();
    }

    /**
     * Handle errors caught by SWT GDK error handler
     */
    public static void handleGdkError() {
        OS.gdk_flush();
        int errorCode = OS.gdk_error_trap_pop();
        if (errorCode != 0) {
            LOGGER.debug("Uncaught GDK X error: " + errorCode);
        }
    }

    /**
     * @inheritDoc
     */
    public void postEvent(AWTEvent event) {
        registerErrorHandler();
        super.postEvent(event);
        handleGdkError();
    }

    /**
     * @inheritDoc
     */
    protected void dispatchEvent(AWTEvent event) {
        registerErrorHandler();
        super.dispatchEvent(event);
        handleGdkError();
    }

    /**
     * @inheritDoc
     */
    public AWTEvent getNextEvent() throws InterruptedException {
        registerErrorHandler();
        AWTEvent event = super.getNextEvent();
        handleGdkError();
        return event;
    }

    /**
     * @inheritDoc
     */
    public synchronized AWTEvent peekEvent() {
        registerErrorHandler();
        AWTEvent event = super.peekEvent();
        handleGdkError();
        return event;
    }

    /**
     * @inheritDoc
     */
    public synchronized AWTEvent peekEvent(int id) {
        registerErrorHandler();
        AWTEvent event = super.peekEvent(id);
        handleGdkError();
        return event;
    }

    /**
     * @inheritDoc
     */
    protected void pop() throws EmptyStackException {
        registerErrorHandler();
        super.pop();
        handleGdkError();
    }

    /**
     * @inheritDoc
     */
    public synchronized void push(EventQueue newEventQueue) {
        registerErrorHandler();
        super.push(newEventQueue);
        handleGdkError();
    }
}
