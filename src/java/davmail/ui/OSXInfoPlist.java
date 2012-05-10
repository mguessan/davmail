/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2012  Mickael Guessant
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

import davmail.util.IOUtil;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Handle OSX Info.plist file access
 */
public class OSXInfoPlist {
    protected static final Logger LOGGER = Logger.getLogger(OSXInfoPlist.class);
    protected static final String INFO_PLIST_PATH = "Contents/Info.plist";

    protected static boolean isOSX() {
        return System.getProperty("os.name").toLowerCase().startsWith("mac os x");
    }

    protected static String getInfoPlistContent() throws IOException {
        FileInputStream fileInputStream = new FileInputStream(INFO_PLIST_PATH);
        return new String(IOUtil.readFully(fileInputStream));
    }

    /**
     * Test current LSUIElement (hide from dock) value
     *
     * @return true if application is hidden from dock
     */
    public static boolean isHideFromDock() {
        boolean result = false;
        try {
            result = isOSX() && getInfoPlistContent().indexOf("<key>LSUIElement</key><string>1</string>") >= 0;
        } catch (IOException e) {
            LOGGER.warn("Unable to update Info.plist", e);
        }
        return result;
    }

    /**
     * Update LSUIElement (hide from dock) value
     *
     * @param hideFromDock new hide from dock value
     */
    public static void setOSXHideFromDock(boolean hideFromDock) {
        try {
            if (isOSX()) {
                boolean currentHideFromDock = isHideFromDock();
                if (currentHideFromDock != hideFromDock) {
                    String content = getInfoPlistContent();
                    FileOutputStream fileOutputStream = new FileOutputStream(INFO_PLIST_PATH);
                    try {
                        fileOutputStream.write(content.replaceFirst(
                                "<key>LSUIElement</key><string>" + (currentHideFromDock ? "1" : "0") + "</string>",
                                "<key>LSUIElement</key><string>" + (hideFromDock ? "1" : "0") + "</string>").getBytes("UTF-8"));
                    } finally {
                        fileOutputStream.close();
                    }
                    fileOutputStream.close();
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to update Info.plist", e);
        }
    }
}
