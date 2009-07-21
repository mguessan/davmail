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
package davmail;

import davmail.exception.DavMailException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Internationalization message.
 */
public class BundleMessage {
    public static final Locale ROOT_LOCALE = new Locale("", "");
    protected static final String MESSAGE_BUNDLE_NAME = "davmailmessages";
    protected final String key;
    private final Object[] arguments;

    public BundleMessage(String key, Object... arguments) {
        this.key = key;
        this.arguments = arguments;
    }

    public String format() {
        return format(null);
    }

    public String format(Locale locale) {
        return BundleMessage.format(locale, key, arguments);
    }

    public String formatLog() {
        return format(ROOT_LOCALE);
    }

    protected static ResourceBundle getBundle(Locale locale) {
        if (locale == null) {
            return ResourceBundle.getBundle(MESSAGE_BUNDLE_NAME);
        } else {
            return ResourceBundle.getBundle(MESSAGE_BUNDLE_NAME, locale);
        }
    }

    public static String format(String key, Object... arguments) {
        return format(null, key, arguments);
    }

    public static String format(Locale locale, String key, Object... arguments) {
        Object[] formattedArguments = null;
        if (arguments != null) {
            formattedArguments = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i] instanceof BundleMessage) {
                    formattedArguments[i] = ((BundleMessage) arguments[i]).format(locale);
                } else if (arguments[i] instanceof BundleMessageList) {
                    StringBuilder buffer = new StringBuilder();
                    for (BundleMessage bundleMessage : (BundleMessageList) arguments[i]) {
                        buffer.append(bundleMessage.format(locale));
                    }
                    formattedArguments[i] = buffer.toString();
                } else if (arguments[i] instanceof DavMailException) {
                    formattedArguments[i] = ((DavMailException) arguments[i]).getMessage(locale);
                } else if (arguments[i] instanceof Throwable) {
                    formattedArguments[i] = ((Throwable) arguments[i]).getMessage();
                    if (formattedArguments[i] == null) {
                        formattedArguments[i] = arguments[i].toString();
                    }
                } else {
                    formattedArguments[i] = arguments[i];
                }
            }
        }
        return MessageFormat.format(getBundle(locale).getString(key), formattedArguments);
    }

    @Override
    public String toString() {
        return formatLog();
    }

    public static String formatLog(String key, Object... arguments) {
        return format(ROOT_LOCALE, key, arguments);
    }

    public static String getExceptionLogMessage(BundleMessage message, Exception e) {
        return getExceptionMessage(message, e, ROOT_LOCALE);
    }

    public static String getExceptionMessage(BundleMessage message, Exception e) {
        return getExceptionMessage(message, e, null);
    }

    public static String getExceptionMessage(BundleMessage message, Exception e, Locale locale) {
        StringBuilder buffer = new StringBuilder();
        if (message != null) {
            buffer.append(message.format(locale)).append(' ');
        }
        if (e instanceof DavMailException) {
            buffer.append(((DavMailException) e).getMessage(locale));
        } else if (e.getMessage() != null) {
            buffer.append(e.getMessage());
        } else {
            buffer.append(e.toString());
        }
        return buffer.toString();
    }

    public static class BundleMessageList extends ArrayList<BundleMessage> {
    }
}
