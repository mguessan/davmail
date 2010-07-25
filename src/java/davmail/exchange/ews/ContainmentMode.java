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
package davmail.exchange.ews;

/**
 * Contains search mode.
 */
@SuppressWarnings({"UnusedDeclaration"})
public class ContainmentMode extends AttributeOption {
    private ContainmentMode(String value) {
        super("ContainmentMode", value);
    }

    /**
     * Full String.
     */
    public static final ContainmentMode FullString = new ContainmentMode("FullString");
    /**
     * Starts with.
     */
    public static final ContainmentMode Prefixed = new ContainmentMode("Prefixed");
    /**
     * Contains
     */
    public static final ContainmentMode Substring = new ContainmentMode("Substring");
    public static final ContainmentMode PrefixOnWords = new ContainmentMode("PrefixOnWords");
    public static final ContainmentMode ExactPhrase = new ContainmentMode("ExactPhrase");
}
