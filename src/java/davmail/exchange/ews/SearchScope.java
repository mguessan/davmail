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
 * ResolveNames search scope.
 */
@SuppressWarnings({"JavaDoc", "UnusedDeclaration"})
public final class SearchScope extends AttributeOption {
    private SearchScope(String value) {
        super("SearchScope", value);
    }

    public static final SearchScope ActiveDirectory = new SearchScope("ActiveDirectory");
    public static final SearchScope ActiveDirectoryContacts = new SearchScope("ActiveDirectoryContacts");
    public static final SearchScope Contacts = new SearchScope("Contacts");
    public static final SearchScope ContactsActiveDirectory = new SearchScope("ContactsActiveDirectory");
}
