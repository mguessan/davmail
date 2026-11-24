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

package davmail.exchange.graph;

import davmail.exchange.AbstractExchangeSessionTestCase;
import davmail.exchange.ExchangeSession;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.UUID;

/**
 * Tasks created over CalDAV keep the client UID and item name, see https://github.com/mguessan/davmail/issues/535
 */
public class TestGraphExchangeSessionTasks extends AbstractExchangeSessionTestCase {

    protected String buildVTodo(String uid, String summary) {
        return "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//davmail//test//EN\r\n" +
                "BEGIN:VTODO\r\n" +
                "UID:" + uid + "\r\n" +
                "DTSTAMP:20261015T120000Z\r\n" +
                "SUMMARY:" + summary + "\r\n" +
                "STATUS:NEEDS-ACTION\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n";
    }

    protected int countTasks(String folderPath, String uid, String itemName) throws IOException {
        int count = 0;
        for (ExchangeSession.Event event : session.searchTasksOnly(folderPath)) {
            if (event.getBody().contains("UID:" + uid)) {
                assertEquals(itemName, event.getName());
                count++;
            }
        }
        return count;
    }

    public void testTaskKeepsClientUid() throws IOException {
        assertTrue(session instanceof GraphExchangeSession);
        GraphExchangeSession graphExchangeSession = (GraphExchangeSession) session;
        // CalDAV clients such as org-caldav put tasks in the main calendar
        String folderPath = "/users/" + session.getEmail() + "/calendar";
        String uid = "davmail-test-" + UUID.randomUUID();
        String itemName = uid + ".EML";
        try {
            ExchangeSession.ItemResult itemResult = session.createOrUpdateItem(folderPath, itemName, buildVTodo(uid, "client uid test"), null, "*");
            assertEquals(HttpStatus.SC_CREATED, itemResult.status);

            // get by client item name right after create
            assertTrue(session.getItem(folderPath, itemName).getBody().contains("UID:" + uid));
            // listing returns the client UID and item name
            assertEquals(1, countTasks(folderPath, uid, itemName));

            // item name cache is empty after a restart: update by client item name must not create a new task
            graphExchangeSession.urlcompnameToIdMap.clear();
            itemResult = session.createOrUpdateItem(folderPath, itemName, buildVTodo(uid, "client uid test updated"), null, null);
            assertEquals(HttpStatus.SC_OK, itemResult.status);
            assertEquals(1, countTasks(folderPath, uid, itemName));

            // get by client item name after a restart
            graphExchangeSession.urlcompnameToIdMap.clear();
            assertTrue(session.getItem(folderPath, itemName).getBody().contains("SUMMARY:client uid test updated"));
        } finally {
            graphExchangeSession.urlcompnameToIdMap.clear();
            session.deleteItem(folderPath, itemName);
        }
        assertEquals(0, countTasks(folderPath, uid, itemName));
    }
}
