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
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;

/**
 * Experimental test for delta sync.
 */
public class TestGraphExchangeSessionDeltaSync extends AbstractExchangeSessionTestCase {
    public static final Logger LOGGER = Logger.getLogger(TestGraphExchangeSessionDeltaSync.class);


    public void testSearchInboxDelta() throws IOException, JSONException, InterruptedException {
        String folderPath = "INBOX";
        GraphExchangeSession session = (GraphExchangeSession) this.session;

        GraphExchangeSession.FolderId folderId = session.getFolderId(folderPath);

        String initialDeltaLink = session.getDeltaLink(session.getFolderId(folderPath));

        GraphExchangeSession.MessageList messages = session.searchMessages(folderPath);

        String deltaLink = initialDeltaLink;

        while (deltaLink != null) {
            LOGGER.debug("deltaLink " + deltaLink);
            GraphRequestBuilder httpRequestBuilder = new GraphRequestBuilder().setMethod(HttpGet.METHOD_NAME).setUrl(deltaLink);
            GraphExchangeSession.GraphIterator graphIterator = session.executeSearchRequest(httpRequestBuilder);
            while (graphIterator.hasNext()) {
                JSONObject deltaMessage = graphIterator.next();
                LOGGER.debug(deltaMessage.toString(4));
                String id = deltaMessage.getString("id");
                if (deltaMessage.has("@removed")) {
                    // message deleted
                    for (ExchangeSession.Message message : messages) {
                        if (((GraphExchangeSession.Message)message).id.equals(id)) {
                            messages.remove(message);
                            break;
                        }
                    }
                } else {
                    // insert or update
                    GraphExchangeSession.Message currentMessage = null;
                    for (ExchangeSession.Message message : messages) {
                        if (((GraphExchangeSession.Message)message).id.equals(id)) {
                            currentMessage = (GraphExchangeSession.Message) message;
                            messages.remove(message);
                            break;
                        }
                    }
                    // fetch new message
                    GraphExchangeSession.Message newMessage = (GraphExchangeSession.Message) session.getMessage(folderId, id);
                    // restore imap uid
                    if (currentMessage != null) {
                        LOGGER.debug("Restoring imapUid " + newMessage.imapUid + " -> "+ currentMessage.imapUid);
                        newMessage.imapUid = currentMessage.imapUid;
                    }
                    messages.add(newMessage);
                }
            }
            deltaLink = graphIterator.getDeltaLink();
            Thread.sleep(5000);
        }

    }


}
