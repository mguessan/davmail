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
package davmail.exchange.dav;

import davmail.exchange.AbstractExchangeSessionTestCase;

import java.io.IOException;

/**
 * Webdav specific unit tests
 */
public class TestDavExchangeSession extends AbstractExchangeSessionTestCase {
    DavExchangeSession davSession;

    public void setUp() throws IOException {
        super.setUp();
        davSession = ((DavExchangeSession) session);
    }

    public void testGetFolderPath() {
        String mailPath = davSession.getFolderPath("");
        String rootPath = davSession.getFolderPath("/users/");

        assertEquals(mailPath + davSession.inboxName, davSession.getFolderPath("INBOX"));
        assertEquals(mailPath + davSession.deleteditemsName, davSession.getFolderPath("Trash"));
        assertEquals(mailPath + davSession.sentitemsName, davSession.getFolderPath("Sent"));
        assertEquals(mailPath + davSession.draftsName, davSession.getFolderPath("Drafts"));

        assertEquals(mailPath + davSession.inboxName + "/test", davSession.getFolderPath("INBOX/test"));
        assertEquals(mailPath + davSession.deleteditemsName + "/test", davSession.getFolderPath("Trash/test"));
        assertEquals(mailPath + davSession.sentitemsName + "/test", davSession.getFolderPath("Sent/test"));
        assertEquals(mailPath + davSession.draftsName + "/test", davSession.getFolderPath("Drafts/test"));

        // TODO: may be wrong, should return full url, public folders may be located on another server
        assertEquals("/public", davSession.getFolderPath("/public"));
        assertEquals("/public/test", davSession.getFolderPath("/public/test"));

        // caldav folder paths
        assertEquals(mailPath, davSession.getFolderPath("/users/"+davSession.getEmail()));
        assertEquals(mailPath, davSession.getFolderPath("/users/"+davSession.getEmail()+ '/'));
        assertEquals(mailPath, davSession.getFolderPath("/users/"+davSession.getAlias()));
        assertEquals(mailPath, davSession.getFolderPath("/users/"+davSession.getAlias()+ '/'));

        assertEquals(mailPath, davSession.getFolderPath("/users/"+davSession.getEmail().toUpperCase()));
        assertEquals(mailPath, davSession.getFolderPath("/users/"+davSession.getEmail().toLowerCase()));
        assertEquals(mailPath, davSession.getFolderPath("/users/"+davSession.getAlias().toUpperCase()));
        assertEquals(mailPath, davSession.getFolderPath("/users/"+davSession.getAlias().toLowerCase()));


        assertEquals(mailPath+"subfolder", davSession.getFolderPath("/users/"+davSession.getAlias()+ "/subfolder"));
        assertEquals(mailPath+"subfolder/", davSession.getFolderPath("/users/"+davSession.getAlias()+ "/subfolder/"));

        assertEquals(rootPath+"anotheruser/", davSession.getFolderPath("/users/anotheruser"));
        assertEquals(rootPath+"anotheruser/subfolder", davSession.getFolderPath("/users/anotheruser/subfolder"));

        assertEquals(mailPath+davSession.inboxName, davSession.getFolderPath("/users/"+davSession.getEmail()+"/inbox"));
        assertEquals(mailPath+davSession.inboxName+"/subfolder", davSession.getFolderPath("/users/"+davSession.getEmail()+"/inbox/subfolder"));

        assertEquals(mailPath+davSession.calendarName, davSession.getFolderPath("/users/"+davSession.getEmail()+"/calendar"));
        assertEquals(mailPath+davSession.contactsName, davSession.getFolderPath("/users/"+davSession.getEmail()+"/contacts"));
        assertEquals(mailPath+davSession.contactsName, davSession.getFolderPath("/users/"+davSession.getEmail()+"/addressbook"));

        assertEquals(rootPath+"anotherUser/"+davSession.inboxName, davSession.getFolderPath("/users/anotherUser/inbox"));
        assertEquals(rootPath+"anotherUser/"+davSession.calendarName, davSession.getFolderPath("/users/anotherUser/calendar"));
        assertEquals(rootPath+"anotherUser/"+davSession.contactsName, davSession.getFolderPath("/users/anotherUser/contacts"));

        // do not replace i18n names
        assertEquals(mailPath+"Inbox", davSession.getFolderPath("/users/"+davSession.getEmail()+"/Inbox"));
        assertEquals(mailPath+"Calendar", davSession.getFolderPath("/users/"+davSession.getEmail()+"/Calendar"));
        assertEquals(mailPath+"Contacts", davSession.getFolderPath("/users/"+davSession.getEmail()+"/Contacts"));
    }
}
