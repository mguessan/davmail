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

import davmail.Settings;

import java.io.IOException;
import java.util.List;

/**
 * Test contact search.
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestExchangeSessionSearchContact extends AbstractExchangeSessionTestCase {
    public void testSearchPublicContacts() throws IOException {
        String folderPath = Settings.getProperty("davmail.publicContactFolder");
        List<ExchangeSession.Contact> contacts = session.searchContacts(folderPath, ExchangeSession.CONTACT_ATTRIBUTES, null);
        int count = 0;
        for (ExchangeSession.Contact contact : contacts) {
            System.out.println("Contact " + (++count) + '/' + contacts.size() + session.getItem(folderPath, contact.getName()));
        }
    }

    public void testSearchPublicContactsWithPicture() throws IOException {
        String folderPath = Settings.getProperty("davmail.publicContactFolder");
        List<ExchangeSession.Contact> contacts = session.searchContacts(folderPath, ExchangeSession.CONTACT_ATTRIBUTES, session.isTrue("haspicture"));
        int count = 0;
        for (ExchangeSession.Contact contact : contacts) {
            System.out.println("Contact " + (++count) + '/' + contacts.size() + contact.getBody());
            assertNotNull(session.getContactPhoto(contact));
        }
    }

}
