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

import davmail.exchange.XMLStreamUtil;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * ConvertId implementation to retrieve primary mailbox address
 */
public class ConvertIdMethod extends EWSMethod {

    /**
     * Build Resolve Names method
     *
     * @param value search value
     */
    public ConvertIdMethod(String value) {
        super("SourceIds", "ConvertId", "ResponseMessages");
        addMethodOption(new AttributeOption("DestinationFormat", "EwsId"));
        unresolvedEntry = new ElementOption("m:SourceIds",
                new AlternateId("EwsId", value));
    }

    @Override
    protected Item handleItem(XMLStreamReader reader) throws XMLStreamException {
        Item responseItem = new Item();
        responseItem.type = "AlternateId";
        // skip to AlternateId
        while (reader.hasNext() && !XMLStreamUtil.isStartTag(reader, "AlternateId")) {
            reader.next();
        }
        if (XMLStreamUtil.isStartTag(reader, "AlternateId")) {
            String mailbox = reader.getAttributeValue(null, "Mailbox");
            if (mailbox != null) {
                responseItem.put("Mailbox", mailbox);
            }
            while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "AlternateId")) {
                reader.next();
            }
        }
        return responseItem;
    }

}
