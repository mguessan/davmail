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
import davmail.exchange.dav.DavExchangeSession;
import davmail.exchange.ews.EwsExchangeSession;
import davmail.http.DavGatewaySSLProtocolSocketFactory;
import junit.framework.TestCase;
import org.apache.log4j.Level;

import java.io.IOException;
import java.util.List;

/**
 * Test Exchange adapter methods.
 */
public class TestExchangeAdapter extends TestCase {
    ExchangeSession davSession;
    ExchangeSession ewsSession;

    @Override
    public void setUp() throws IOException {
        if (davSession == null) {
            Settings.setConfigFilePath("davmail.properties");
            Settings.load();
            DavGatewaySSLProtocolSocketFactory.register();
            davSession = new DavExchangeSession(Settings.getProperty("davmail.url"),
                    Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"));
            ewsSession = new EwsExchangeSession(Settings.getProperty("davmail.url"),
                    Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"));
            Settings.setLoggingLevel("httpclient.wire", Level.INFO);
        }
    }

    public void assertEquals(ExchangeSession.Folder davFolder, ExchangeSession.Folder ewsFolder) {
        assertNotNull(ewsFolder);
        assertEquals(davFolder.folderPath, ewsFolder.folderPath);
        assertEquals(davFolder.folderClass, ewsFolder.folderClass);
        assertEquals(davFolder.hasChildren, ewsFolder.hasChildren);
        assertEquals(davFolder.unreadCount, ewsFolder.unreadCount);

        assertEquals(davFolder.isCalendar(), false);
        assertEquals(ewsFolder.isCalendar(), false);

        assertEquals(davFolder.isContact(), false);
        assertEquals(ewsFolder.isContact(), false);

        assertEquals(davFolder.noInferiors, false);
        assertEquals(ewsFolder.noInferiors, false);

        assertEquals(davFolder.getFlags(), ewsFolder.getFlags());
        assertEquals(davFolder.etag.substring(0, ewsFolder.ctag.length()-1)+ 'Z', ewsFolder.etag);


        assertNotNull(davFolder.ctag);
        assertNotNull(ewsFolder.ctag);
        // dav and ews ctags are still different: dav contentag has milliseconds info
        assertEquals(davFolder.ctag.substring(0, ewsFolder.ctag.length()-1)+ 'Z', ewsFolder.ctag);
        
    }

    public void testGetInbox() throws IOException {
        ExchangeSession.Folder davFolder = davSession.getFolder("INBOX");
        ExchangeSession.Folder ewsFolder = ewsSession.getFolder("INBOX");
        assertEquals(davFolder, ewsFolder);
    }

    public void testGetSubFolder() throws IOException {
        ExchangeSession.Folder ewsFolder = ewsSession.getFolder("INBOX/bbbb");
    }

    public void testFindFolder() throws IOException {
        List<ExchangeSession.Folder> davFolders = davSession.getSubFolders("", false);
        Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
        List<ExchangeSession.Folder> ewsFolders = ewsSession.getSubFolders("", false);
        assertEquals(davFolders.size(), ewsFolders.size());
    }

    public void testFindPublicFolder() throws IOException {
        List<ExchangeSession.Folder> davFolders = davSession.getSubFolders("/public", false);
        Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
        List<ExchangeSession.Folder> ewsFolders = ewsSession.getSubFolders("/public", false);
        assertEquals(davFolders.size(), ewsFolders.size());
    }

    public void testFindFolders() throws IOException {
        List<ExchangeSession.Folder> davFolders = davSession.getSubFolders("/public", null, true);
        System.out.println(davFolders);
    }

    public void testSearchMessages() throws IOException {
        ExchangeSession.MessageList messages = davSession.searchMessages("INBOX");
        for (ExchangeSession.Message message:messages) {
            System.out.println(message);
        }
    }

    public void testSearchEvents() throws IOException {
        List<ExchangeSession.Event> events = davSession.getAllEvents("calendar");
        for (ExchangeSession.Event event:events) {
            System.out.println(event);
        }
    }

}
