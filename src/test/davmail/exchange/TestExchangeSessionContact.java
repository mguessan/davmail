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
package davmail.exchange;

import davmail.exchange.dav.DavExchangeSession;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test ExchangeSession contact features.
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestExchangeSessionContact extends AbstractExchangeSessionTestCase {

    public void testSearchContacts() throws IOException {
        List<ExchangeSession.Contact> contacts = session.searchContacts(ExchangeSession.CONTACTS, ExchangeSession.CONTACT_ATTRIBUTES, null);
        for (ExchangeSession.Contact contact : contacts) {
            System.out.println(session.getItem(ExchangeSession.CONTACTS, contact.getName()));
        }
    }

    public void testSearchContactsUidOnly() throws IOException {
        Set<String> attributes = new HashSet<String>();
        attributes.add("uid");
        List<ExchangeSession.Contact> contacts = session.searchContacts(ExchangeSession.CONTACTS, attributes, null);
        for (ExchangeSession.Contact contact : contacts) {
            System.out.println(contact);
        }
    }

    public void testSearchContactsByUid() throws IOException {
        Set<String> attributes = new HashSet<String>();
        attributes.add("uid");
        List<ExchangeSession.Contact> contacts = session.searchContacts(ExchangeSession.CONTACTS, attributes, null);
        for (ExchangeSession.Contact contact : contacts) {
            System.out.println(session.searchContacts(ExchangeSession.CONTACTS, attributes, session.equals("uid", contact.get("uid"))));
        }
    }
}
