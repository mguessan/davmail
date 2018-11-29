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

package davmail.exchange.auth;

import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import org.apache.log4j.Logger;

public class JSLogger {
    private static final Logger LOGGER = Logger.getLogger(JSLogger.class);
    public void log(String message) {
        LOGGER.info(message);
    }

    public static void register(WebEngine webEngine) {
        JSObject window = (JSObject) webEngine.executeScript("window");
        window.setMember("davmail", new JSLogger());
        webEngine.executeScript("console.log = function(message) { davmail.log(message); }");
    }
}
