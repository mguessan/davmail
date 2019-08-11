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

import java.awt.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class OSXHandler implements InvocationHandler {
    private final OSXTrayInterface davGatewayTray;

    public OSXHandler(OSXTrayInterface davGatewayTray) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        this.davGatewayTray = davGatewayTray;
        addEventHandlers();
    }

    public static final boolean IS_JAVA9 = Double.parseDouble(System.getProperty("java.specification.version")) >= 1.9;

    public void addEventHandlers() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {

        Class<?> applicationClass;
        Class<?> aboutHandlerClass;
        Class<?> preferencesHandlerClass;

        Object application;
        if (IS_JAVA9) {
            applicationClass = Class.forName("java.awt.Desktop");
            application = Desktop.getDesktop();
            aboutHandlerClass = Class.forName("java.awt.desktop.AboutHandler");
            preferencesHandlerClass = Class.forName("java.awt.desktop.PreferencesHandler");
        } else {
            applicationClass = Class.forName("com.apple.eawt.Application");
            application = applicationClass.getMethod("getApplication").invoke(null);
            aboutHandlerClass = Class.forName("com.apple.eawt.AboutHandler");
            preferencesHandlerClass = Class.forName("com.apple.eawt.PreferencesHandler");
        }

        Object proxy = Proxy.newProxyInstance(OSXHandler.class.getClassLoader(), new Class<?>[]{
                aboutHandlerClass, preferencesHandlerClass}, this);

        applicationClass.getDeclaredMethod("setAboutHandler", aboutHandlerClass).invoke(application, proxy);
        applicationClass.getDeclaredMethod("setPreferencesHandler", preferencesHandlerClass).invoke(application, proxy);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if ("handleAbout".equals(method.getName())) {
            davGatewayTray.about();
        } else if ("handlePreferences".equals(method.getName())) {
            davGatewayTray.preferences();
        }
        return null;
    }

}
