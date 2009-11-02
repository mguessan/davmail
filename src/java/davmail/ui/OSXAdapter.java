/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
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
package davmail.ui;

import java.lang.reflect.*;

/**
 * Reflection based MacOS handler
 */
public class OSXAdapter implements InvocationHandler {

    protected final Object targetObject;
    protected final Method targetMethod;
    protected final String proxySignature;

    static Constructor<?> macOSXApplication;

    /**
     * Pass this method an Object and Method equipped to perform application shutdown logic.
     * The method passed should return a boolean stating whether or not the quit should occur
     *
     * @param target      target object
     * @param quitHandler quit method
     * @throws InvocationTargetException on error
     * @throws ClassNotFoundException    on error
     * @throws NoSuchMethodException     on error
     * @throws InstantiationException    on error
     * @throws IllegalAccessException    on error
     */
    public static void setQuitHandler(Object target, Method quitHandler) throws InvocationTargetException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        setHandler(new OSXAdapter("handleQuit", target, quitHandler));
    }

    /**
     * Pass this method an Object and Method equipped to display application info
     * They will be called when the About menu item is selected from the application menu
     *
     * @param target       target object
     * @param aboutHandler about method
     * @throws InvocationTargetException on error
     * @throws ClassNotFoundException    on error
     * @throws NoSuchMethodException     on error
     * @throws InstantiationException    on error
     * @throws IllegalAccessException    on error
     */
    public static void setAboutHandler(Object target, Method aboutHandler) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, ClassNotFoundException, InstantiationException {
        boolean enableAboutMenu = (target != null && aboutHandler != null);
        if (enableAboutMenu) {
            setHandler(new OSXAdapter("handleAbout", target, aboutHandler));
        }
        Method enableAboutMethod = macOSXApplication.getClass().getDeclaredMethod("setEnabledAboutMenu", new Class[]{boolean.class});
        enableAboutMethod.invoke(macOSXApplication, enableAboutMenu);
    }

    /**
     * Pass this method an Object and a Method equipped to display application options.
     * They will be called when the Preferences menu item is selected from the application menu
     *
     * @param target       target object
     * @param prefsHandler preferences method
     * @throws InvocationTargetException on error
     * @throws ClassNotFoundException    on error
     * @throws NoSuchMethodException     on error
     * @throws InstantiationException    on error
     * @throws IllegalAccessException    on error
     */
    public static void setPreferencesHandler(Object target, Method prefsHandler) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, ClassNotFoundException, InstantiationException {
        boolean enablePrefsMenu = (target != null && prefsHandler != null);
        if (enablePrefsMenu) {
            setHandler(new OSXAdapter("handlePreferences", target, prefsHandler));
        }
        Method enablePrefsMethod = macOSXApplication.getClass().getDeclaredMethod("setEnabledPreferencesMenu", new Class[]{boolean.class});
        enablePrefsMethod.invoke(macOSXApplication, enablePrefsMenu);
    }

    /**
     * Pass this method an Object and a Method equipped to handle document events from the Finder.
     * Documents are registered with the Finder via the CFBundleDocumentTypes dictionary in the
     * application bundle's Info.plist
     *
     * @param target      target object
     * @param fileHandler file method
     * @throws InvocationTargetException on error
     * @throws ClassNotFoundException    on error
     * @throws NoSuchMethodException     on error
     * @throws InstantiationException    on error
     * @throws IllegalAccessException    on error
     */
    public static void setFileHandler(Object target, Method fileHandler) throws InvocationTargetException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        setHandler(new OSXAdapter("handleOpenFile", target, fileHandler) {
            // Override OSXAdapter.callTarget to send information on the
            // file to be opened
            @Override
            public boolean callTarget(Object appleEvent) {
                if (appleEvent != null) {
                    try {
                        Method getFilenameMethod = appleEvent.getClass().getDeclaredMethod("getFilename", (Class[]) null);
                        String filename = (String) getFilenameMethod.invoke(appleEvent, (Object[]) null);
                        this.targetMethod.invoke(this.targetObject, filename);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
                return true;
            }
        });
    }

    /**
     * setHandler creates a Proxy object from the passed OSXAdapter and adds it as an ApplicationListener.
     *
     * @param adapter OSX adapter
     * @throws InvocationTargetException on error
     * @throws ClassNotFoundException    on error
     * @throws NoSuchMethodException     on error
     * @throws InstantiationException    on error
     * @throws IllegalAccessException    on error
     */
    public static void setHandler(OSXAdapter adapter) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
        if (macOSXApplication == null) {
            macOSXApplication = (Constructor<?>) applicationClass.getConstructor((Class[]) null).newInstance((Object[]) null);
        }
        Class applicationListenerClass = Class.forName("com.apple.eawt.ApplicationListener");
        Method addListenerMethod = applicationClass.getDeclaredMethod("addApplicationListener", new Class[]{applicationListenerClass});
        // Create a proxy object around this handler that can be reflectively added as an Apple ApplicationListener
        Object osxAdapterProxy = Proxy.newProxyInstance(OSXAdapter.class.getClassLoader(), new Class[]{applicationListenerClass}, adapter);
        addListenerMethod.invoke(macOSXApplication, osxAdapterProxy);
    }

    /**
     * Each OSXAdapter has the name of the EAWT method it intends to listen for (handleAbout, for example),
     * the Object that will ultimately perform the task, and the Method to be called on that Object
     *
     * @param proxySignature proxy signature
     * @param target         target object
     * @param handler        handler method
     */
    protected OSXAdapter(String proxySignature, Object target, Method handler) {
        this.proxySignature = proxySignature;
        this.targetObject = target;
        this.targetMethod = handler;
    }

    /**
     * Override this method to perform any operations on the event
     * that comes with the various callbacks.
     * See setFileHandler above for an example
     *
     * @param appleEvent apple event object
     * @return true on success
     * @throws InvocationTargetException on error
     * @throws IllegalAccessException    on error
     */
    public boolean callTarget(Object appleEvent) throws InvocationTargetException, IllegalAccessException {
        Object result = targetMethod.invoke(targetObject, (Object[]) null);
        return result == null || Boolean.valueOf(result.toString());
    }

    /**
     * InvocationHandler implementation.
     * This is the entry point for our proxy object; it is called every time an ApplicationListener method is invoked
     *
     * @param proxy  proxy object
     * @param method handler method
     * @param args   method arguments
     * @return null
     * @throws Throwable on error
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (isCorrectMethod(method, args)) {
            boolean handled = callTarget(args[0]);
            setApplicationEventHandled(args[0], handled);
        }
        // All of the ApplicationListener methods are void; return null regardless of what happens
        return null;
    }

    //
    //
    /**
     * Compare the method that was called to the intended method when the OSXAdapter instance was created
     * (e.g. handleAbout, handleQuit, handleOpenFile, etc.).
     *
     * @param method handler method
     * @param args   method arguments
     * @return true if method is correct
     */
    protected boolean isCorrectMethod(Method method, Object[] args) {
        return (targetMethod != null && proxySignature.equals(method.getName()) && args.length == 1);
    }

    /**
     * It is important to mark the ApplicationEvent as handled and cancel the default behavior.
     * This method checks for a boolean result from the proxy method and sets the event accordingly
     *
     * @param event   event object
     * @param handled true if event handled
     * @throws NoSuchMethodException     on error
     * @throws InvocationTargetException on error
     * @throws IllegalAccessException    on error
     */
    protected void setApplicationEventHandled(Object event, boolean handled) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (event != null) {
            Method setHandledMethod = event.getClass().getDeclaredMethod("setHandled", new Class[]{boolean.class});
            // If the target method returns a boolean, use that as a hint
            setHandledMethod.invoke(event, handled);
        }
    }
}