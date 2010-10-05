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
        while (reader.hasNext() && !XMLStreamUtil.isStartTag(reader, "Resolution")) {
            reader.next();
        }
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "Resolution")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Mailbox".equals(tagLocalName)) {
                    handleMailbox(reader, responseItem);
                } else if ("Contact".equals(tagLocalName)) {
                    handleContact(reader, responseItem);
                }
            }
        }
        return responseItem;
    }

    protected void handleMailbox(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "Mailbox")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Name".equals(tagLocalName)) {
                    responseItem.put(tagLocalName, XMLStreamUtil.getElementText(reader));
                }
            }
        }
    }

    protected void handleContact(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "Contact")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("EmailAddresses".equals(tagLocalName)) {
                    handleEmailAddresses(reader, responseItem);
                } else if ("PhysicalAddresses".equals(tagLocalName)) {
                    handlePhysicalAddresses(reader, responseItem);
                } else if ("PhoneNumbers".equals(tagLocalName)) {
                    handlePhoneNumbers(reader, responseItem);
                } else {
                    responseItem.put(tagLocalName, XMLStreamUtil.getElementText(reader));
                }
            }
        }
    }

    protected void handlePhysicalAddresses(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "PhysicalAddresses")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                // TODO
            }
        }
    }

    protected void handlePhoneNumbers(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "PhoneNumbers")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                // TODO
            }
        }
    }

    @Override
    protected void handleEmailAddresses(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "EmailAddresses")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Entry".equals(tagLocalName)) {
                    String key = getAttributeValue(reader, "Key");
                    String value = XMLStreamUtil.getElementText(reader);
                    if (value != null && (value.startsWith("smtp:") || value.startsWith("SMTP:"))) {
                        value = value.substring(5);
                    }
                    responseItem.put(key, value);
                }
            }
        }
    }

}
