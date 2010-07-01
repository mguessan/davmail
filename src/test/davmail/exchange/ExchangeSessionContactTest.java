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
import davmail.http.DavGatewayHttpClientFacade;
import junit.framework.TestCase;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.jackrabbit.webdav.client.methods.PropPatchMethod;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.apache.log4j.Level;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Test contact search
 */
public class ExchangeSessionContactTest extends AbstractExchangeSessionTestCase {
     @Override
    public void setUp() throws IOException {
        super.setUp();
    }
    /*
    public void testSearchPrivateFlag() throws IOException {
        ExchangeSession.MessageList messageList = session.searchMessages("Contacts", " AND \"http://schemas.microsoft.com/mapi/proptag/0x360003\" = 2");
        assertEquals(1, messageList.size());
    }

    public void testSearchPrivateFlag2() throws IOException {
        ExchangeSession.MessageList messageList = session.searchMessages("Contacts", " AND \"http://schemas.microsoft.com/mapi/sensitivity\" = 2");
        assertEquals(1, messageList.size());
    }
    public void testSearchPrivateFlag3() throws IOException {
        ExchangeSession.MessageList messageList = session.searchMessages("Contacts", " AND \"http://schemas.microsoft.com/exchange/sensitivity\" = 2");
        assertEquals(1, messageList.size());
    }

    public void testSearchPrivateFlag4() throws IOException {
        ExchangeSession.MessageList messageList = session.searchMessages("Contacts", " AND Cast(\"http://schemas.microsoft.com/mapi/id/{00062008-0000-0000-C000-000000000046}/0x8506\" as \"boolean\") = true");
        assertEquals(1, messageList.size());
    }
        //
    public void testUnsetPrivateFlag() throws IOException {
        String messageUrl = URIUtil.encodePathQuery(session.getFolderPath("Contacts") + '/' + "test test" + ".EML");
        ArrayList<DavProperty> list = new ArrayList<DavProperty>();
        list.add(new DefaultDavProperty(DavPropertyName.create("sensitivity", Namespace.getNamespace("http://schemas.microsoft.com/mapi/")), "0"));
        PropPatchMethod propPatchMethod = new PropPatchMethod(messageUrl, list);
        DavGatewayHttpClientFacade.executeMethod(session.getHttpClient(), propPatchMethod);
    }

     public void testSetPrivateFlag() throws IOException {
        String messageUrl = URIUtil.encodePathQuery(session.getFolderPath("Contacts") + '/' + "test test" + ".EML");
        ArrayList<DavProperty> list = new ArrayList<DavProperty>();
        list.add(new DefaultDavProperty(DavPropertyName.create("sensitivity", Namespace.getNamespace("http://schemas.microsoft.com/mapi/")), "2"));
        PropPatchMethod propPatchMethod = new PropPatchMethod(messageUrl, list);
        //DavGatewayHttpClientFacade.executeMethod(session.getHttpClient(), propPatchMethod);

        ExchangeSession.MessageList messageList = session.searchMessages("Contacts", " AND Cast(\"http://schemas.microsoft.com/mapi/id/{00062008-0000-0000-C000-000000000046}/0x8506\" as \"boolean\") = true");
        assertEquals(1, messageList.size());
    }   */
}
