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

import davmail.exchange.AbstractExchangeSessionTestCase;
import davmail.exchange.ExchangeSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Webdav specific unit tests
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestEwsExchangeSession extends AbstractExchangeSessionTestCase {
    EwsExchangeSession ewsSession;

    public void setUp() throws IOException {
        super.setUp();
        ewsSession = ((EwsExchangeSession) session);
    }

    public void testResolveNames() throws IOException {
        ResolveNamesMethod resolveNamesMethod = new ResolveNamesMethod("smtp:g");
        ewsSession.executeMethod(resolveNamesMethod);
        List<EWSMethod.Item> items = resolveNamesMethod.getResponseItems();
        for (EWSMethod.Item item : items) {
            System.out.println(item);
        }
    }

    public void testGalFind() throws IOException {
        // find a set of contacts
        Map<String, ExchangeSession.Contact> contacts = ewsSession.galFind(ewsSession.startsWith("cn", "a"), null, 100);
        for (ExchangeSession.Contact contact : contacts.values()) {
            System.out.println(contact);
        }
        if (!contacts.isEmpty()) {
            ExchangeSession.Contact testContact = contacts.values().iterator().next();
            contacts = ewsSession.galFind(ewsSession.isEqualTo("cn", testContact.get("cn")), null, 100);
            assertEquals(1, contacts.size());
            contacts = ewsSession.galFind(ewsSession.isEqualTo("email1", testContact.get("email1")), null, 100);
            assertEquals(1, contacts.size());
            contacts = ewsSession.galFind(ewsSession.startsWith("email1", testContact.get("email1")), null, 100);
            assertEquals(1, contacts.size());
            contacts = ewsSession.galFind(ewsSession.and(ewsSession.isEqualTo("cn", testContact.get("cn")),
                    ewsSession.startsWith("email1", testContact.get("email1"))), null, 100);
            assertEquals(1, contacts.size());
        }
    }

}
