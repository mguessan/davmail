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

import java.io.IOException;
import java.util.UUID;

/**
 * Test ExchangeSession contact features.
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestExchangeSessionContact extends AbstractExchangeSessionTestCase {
    static String itemName;
    /*
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
    } */

    public void testCreateFolder() throws IOException {
        // recreate empty folder
        session.deleteFolder("testcontactfolder");
        session.createContactFolder("testcontactfolder");
    }

    public void testCreateContact() throws IOException {
        itemName = UUID.randomUUID().toString() + ".vcf";
        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("N", "sn", "givenName", "middlename", "personaltitle", "namesuffix");
        vCardWriter.appendProperty("FN", "common name");
        vCardWriter.appendProperty("NICKNAME", "nickname");
        vCardWriter.endCard();

        session.createOrUpdateContact("testcontactfolder", itemName, vCardWriter.toString(), null, null);

    }

    public void testGetContact() throws IOException {
        ExchangeSession.Contact contact = (ExchangeSession.Contact) session.getItem("testcontactfolder", itemName);
        assertEquals("common name", contact.get("cn"));
        assertEquals("sn", contact.get("sn"));
        assertEquals("givenName", contact.get("givenName"));
        assertEquals("middlename", contact.get("middlename"));
        assertEquals("personaltitle", contact.get("personaltitle"));
        assertEquals("namesuffix", contact.get("namesuffix"));
        assertNotNull("lastmodified");
        assertEquals("nickname", contact.get("nickname"));
    }
}
