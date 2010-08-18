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

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.Writer;

/**
 * Get User Configuration method.
 */
public class GetUserConfigurationMethod extends EWSMethod {

    /**
     * Get User Configuration method.
     *
     */
    public GetUserConfigurationMethod() {
        super("UserConfiguration", "GetUserConfiguration");
        folderId = DistinguishedFolderId.getInstance(null, DistinguishedFolderId.Name.root);
    }

    @Override
    protected void writeSoapBody(Writer writer) throws IOException {
        writer.write("<m:UserConfigurationName Name=\"OWA.UserOptions\">");
        folderId.write(writer);
        writer.write("</m:UserConfigurationName>");
        writer.write("<m:UserConfigurationProperties>All</m:UserConfigurationProperties>");
    }

    @Override
    protected void handleCustom(XMLStreamReader reader) throws XMLStreamException {
        if (isStartTag(reader, "UserConfiguration")) {
            responseItems.add(handleUserConfiguration(reader));
        }
    }

    private Item handleUserConfiguration(XMLStreamReader reader) throws XMLStreamException {
        Item responseItem = new Item();
        while (reader.hasNext() && !(isEndTag(reader, "UserConfiguration"))) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagLocalName = reader.getLocalName();
                if ("DictionaryEntry".equals(tagLocalName)) {
                    handleDictionaryEntry(reader, responseItem);
                }
            }
        }
        return responseItem;
    }

    private void handleDictionaryEntry(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        String key = null;
        while (reader.hasNext() && !(isEndTag(reader, "DictionaryEntry"))) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagLocalName = reader.getLocalName();
                if ("Value".equals(tagLocalName)) {
                    if (key == null) {
                        key = reader.getElementText();
                    } else {
                        responseItem.put(key, reader.getElementText());
                    }
                }
            }
        }
    }

}
