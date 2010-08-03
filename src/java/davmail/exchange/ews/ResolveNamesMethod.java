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

/**
 * Resolve Names method.
 */
public class ResolveNamesMethod extends EWSMethod {
    protected static final AttributeOption RETURN_FULL_CONTACT_DATA = new AttributeOption("ReturnFullContactData", "true");

    /**
     * Build Resolve Names method
     *
     * @param value search value
     */
    public ResolveNamesMethod(String value) {
        super("Contact", "ResolveNames", "ResolutionSet");
        addMethodOption(SearchScope.ActiveDirectory);
        addMethodOption(RETURN_FULL_CONTACT_DATA);
        unresolvedEntry = new ElementOption("m:UnresolvedEntry", value);
    }

    @Override
    protected Item handleItem(XMLStreamReader reader) throws XMLStreamException {
        Item responseItem = new Item();
        responseItem.type = "Contact";
        // skip to Contact
        while (reader.hasNext() && !isStartTag(reader, "Contact")) {
            reader.next();
        }
        while (reader.hasNext() && !isEndTag(reader, "Contact")) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagLocalName = reader.getLocalName();
                if ("EmailAddresses".equals(tagLocalName)) {
                    handleAddresses(reader, responseItem);
                } else {
                    responseItem.put(tagLocalName, reader.getElementText());
                }
            }
        }
        return responseItem;
    }

    protected void handleAddresses(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        while (reader.hasNext() && !isEndTag(reader, "EmailAddresses")) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tagLocalName = reader.getLocalName();
                if ("Entry".equals(tagLocalName)) {
                    String key = getAttributeValue(reader, "Key");
                    String value = reader.getElementText();
                    if (value.startsWith("smtp:") || value.startsWith("SMTP:")) {
                        value = value.substring(5);
                    }
                    responseItem.put(key, value);
                }
            }
        }
    }

}
