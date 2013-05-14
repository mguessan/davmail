/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2011  Mickael Guessant
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

import davmail.exchange.AbstractExchangeSessionTestCase;
import davmail.exchange.ExchangeSession;
import davmail.exchange.dav.DavExchangeSession;
import davmail.exchange.dav.ExchangePropFindMethod;
import davmail.exchange.dav.Field;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Test custom Propfind method.
 */
public class TestExchangePropfindMethod extends AbstractExchangeSessionTestCase {
    public void testGetFolder() throws IOException, DavException {
        ExchangeSession.Folder folder = session.getFolder("INBOX");
        assertNotNull(folder);
        DavPropertyNameSet davPropertyNameSet = new DavPropertyNameSet();
           // davPropertyNameSet.add(Field.getPropertyName("displayname"));
        //PropFindMethod propFindMethod = new PropFindMethod(URIUtil.encodePath(((DavExchangeSession)session).getFolderPath(folder.folderPath)));
        //session.httpClient.executeMethod(propFindMethod);
        //propFindMethod.getResponseBodyAsMultiStatus();

        

        ExchangePropFindMethod exchangePropFindMethod = new ExchangePropFindMethod(URIUtil.encodePath(((DavExchangeSession)session).getFolderPath(folder.folderPath)), davPropertyNameSet, 0);
        //PropFindMethod propFindMethod = new PropFindMethod(URIUtil.encodePath(((DavExchangeSession)session).getFolderPath(folder.folderPath)));
        session.httpClient.executeMethod(exchangePropFindMethod);
         MultiStatusResponse response = exchangePropFindMethod.getResponse();
                    DavPropertySet properties = response.getProperties(HttpStatus.SC_OK);
                    System.out.println(properties);
    }
}
