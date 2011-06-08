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
package davmail.ui.browser;

import org.eclipse.swt.program.Program;

import java.net.URI;

/**
 * Wrapper class to call SWT Program class to launch default browser.
 */
public final class SwtDesktopBrowser {
    private SwtDesktopBrowser() {
    }

    /**
     * Open default browser at location URI.
     * User SWT program launch
     *
     * @param location location URI
     */
    public static void browse(URI location) {
        Program.launch(location.toString());
    }

}
