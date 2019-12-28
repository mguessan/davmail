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

import davmail.exchange.ExchangeSession;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.HashMap;

public class TestDavExchangeSessionMessage extends AbstractDavExchangeSessionTestCase {
    public void testMessage() throws IOException, MessagingException {
        davSession.deleteFolder("testfolder");
        davSession.createMessageFolder("testfolder");
        MimeMessage mimeMessage = createMimeMessage();
        String itemName = "space star*dash-ampersand&.EML";
        HashMap<String, String> properties = new HashMap<>();

        davSession.createMessage("testfolder", itemName, properties, mimeMessage);
        ExchangeSession.Folder folder = davSession.getFolder("testfolder");
        folder.loadMessages();
        assertEquals(1, folder.count());
        System.out.println(folder.get(0).permanentUrl+" "+folder.get(0).messageUrl);
        ExchangeSession.Item item = davSession.getItem("testfolder", itemName);
        assertNotNull(item);
    }

}
