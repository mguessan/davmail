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
package davmail.exception;

import davmail.BundleMessage;

import java.io.IOException;
import java.util.Locale;

/**
 * I18 IOException subclass.
 */
public class DavMailException extends IOException {
    private final BundleMessage message;

    /**
     * Create a DavMail exception with the given BundleMessage key and arguments.
     *
     * @param key       message key
     * @param arguments message values
     */
    public DavMailException(String key, Object... arguments) {
        this.message = new BundleMessage(key, arguments);
    }

    /**
     * Get formatted message
     *
     * @return english formatted message
     */
    @Override
    public String getMessage() {
        return message.formatLog();
    }

    /**
     * Get formatted message using locale.
     *
     * @param locale locale
     * @return localized formatted message
     */
    public String getMessage(Locale locale) {
        return message.format(locale);
    }

    /**
     * Get internal exception BundleMessage.
     *
     * @return unformatted message
     */
    public BundleMessage getBundleMessage() {
        return message;
    }
}
