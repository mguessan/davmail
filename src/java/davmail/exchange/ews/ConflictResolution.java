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
 * Item update conflict resolution
 */
public class ConflictResolution extends AttributeOption {
    private ConflictResolution(String value) {
        super("ConflictResolution", value);
    }

    public static final ConflictResolution NeverOverwrite = new ConflictResolution("NeverOverwrite");
    public static final ConflictResolution AutoResolve = new ConflictResolution("AutoResolve");
    public static final ConflictResolution AlwaysOverwrite = new ConflictResolution("AlwaysOverwrite");
}
