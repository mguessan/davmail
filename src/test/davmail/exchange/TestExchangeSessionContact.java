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

        vCardWriter.appendProperty("TEL;TYPE=cell", "mobile");
        vCardWriter.appendProperty("TEL;TYPE=work", "telephoneNumber");
        vCardWriter.appendProperty("TEL;TYPE=home,voice", "homePhone");
        vCardWriter.appendProperty("TEL;TYPE=fax", "facsimiletelephonenumber");
        vCardWriter.appendProperty("TEL;TYPE=pager", "pager");

        vCardWriter.appendProperty("ADR;TYPE=home", null, null, "homeStreet", "homeCity", "homeState", "homePostalCode", "homeCountry");
        vCardWriter.appendProperty("ADR;TYPE=work", "postofficebox", "roomnumber", "street", "l", "st", "postalcode", "co");
        
        vCardWriter.appendProperty("EMAIL;TYPE=work", "email1@local.net");
        vCardWriter.appendProperty("EMAIL;TYPE=home", "email2@local.net");
        vCardWriter.appendProperty("EMAIL;TYPE=other", "email3@local.net");

        vCardWriter.appendProperty("ORG", "o", "department");

        vCardWriter.appendProperty("URL;TYPE=work", "http://local.net");
        vCardWriter.appendProperty("TITLE", "title");
        vCardWriter.appendProperty("NOTE", "description");

        vCardWriter.appendProperty("CUSTOM1", "extensionattribute1");
        vCardWriter.appendProperty("CUSTOM2", "extensionattribute2");
        vCardWriter.appendProperty("CUSTOM3", "extensionattribute3");
        vCardWriter.appendProperty("CUSTOM4", "extensionattribute4");

        vCardWriter.appendProperty("ROLE", "profession");
        vCardWriter.appendProperty("X-AIM", "im");
        //vCardWriter.appendProperty("BDAY", "bday");

        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact("testcontactfolder", itemName, vCardWriter.toString(), null, null);
        assertEquals(201, result.status);

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

        assertEquals("mobile", contact.get("mobile"));
        assertEquals("telephoneNumber", contact.get("telephoneNumber"));
        assertEquals("homePhone", contact.get("homePhone"));
        assertEquals("facsimiletelephonenumber", contact.get("facsimiletelephonenumber"));
        assertEquals("pager", contact.get("pager"));

        assertEquals("homeStreet", contact.get("homeStreet"));
        assertEquals("homeCity", contact.get("homeCity"));
        assertEquals("homeState", contact.get("homeState"));
        assertEquals("homePostalCode", contact.get("homePostalCode"));
        assertEquals("homeCountry", contact.get("homeCountry"));

        assertEquals("postofficebox", contact.get("postofficebox"));
        assertEquals("roomnumber", contact.get("roomnumber"));
        assertEquals("street", contact.get("street"));
        assertEquals("l", contact.get("l"));
        assertEquals("st", contact.get("st"));
        assertEquals("postalcode", contact.get("postalcode"));
        assertEquals("co", contact.get("co"));

        assertEquals("email1@local.net", contact.get("email1"));
        assertEquals("email2@local.net", contact.get("email2"));
        assertEquals("email3@local.net", contact.get("email3"));

        assertEquals("o", contact.get("o"));
        assertEquals("department", contact.get("department"));

        assertEquals("http://local.net", contact.get("businesshomepage"));
        assertEquals("title", contact.get("title"));
        assertEquals("description", contact.get("description"));

        assertEquals("extensionattribute1", contact.get("extensionattribute1"));
        assertEquals("extensionattribute2", contact.get("extensionattribute2"));
        assertEquals("extensionattribute3", contact.get("extensionattribute3"));
        assertEquals("extensionattribute4", contact.get("extensionattribute4"));

        assertEquals("profession", contact.get("profession"));
        assertEquals("im", contact.get("im"));
        //assertEquals("bday", contact.get("bday"));
    }
}
