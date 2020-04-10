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
        addMethodOption(ContactDataShape.AllProperties);
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
                } else if ("EmailAddress".equals(tagLocalName)) {
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
                } else if ("MSExchangeCertificate".equals(tagLocalName)
                        || "UserSMIMECertificate".equals(tagLocalName)) {
                    handleUserCertificate(reader, responseItem, tagLocalName);
                } else if ("ManagerMailbox".equals(tagLocalName)
                        || "Attachments".equals(tagLocalName)
                        || "Categories".equals(tagLocalName)
                        || "InternetMessageHeaders".equals(tagLocalName)
                        || "ResponseObjects".equals(tagLocalName)
                        || "ExtendedProperty".equals(tagLocalName)
                        || "EffectiveRights".equals(tagLocalName)
                        || "CompleteName".equals(tagLocalName)
                        || "Children".equals(tagLocalName)
                        || "Companies".equals(tagLocalName)
                        || "ImAddresses".equals(tagLocalName)
                        || "DirectReports".equals(tagLocalName)) {
                    skipTag(reader, tagLocalName);
                } else {
                    responseItem.put(tagLocalName, XMLStreamUtil.getElementText(reader));
                }
            }
        }
    }

    protected void handlePhysicalAddress(XMLStreamReader reader, Item responseItem, String addressType) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "Entry")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                String value = XMLStreamUtil.getElementText(reader);
                responseItem.put(addressType + tagLocalName, value);
            }
        }
    }

    protected void handlePhysicalAddresses(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "PhysicalAddresses")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Entry".equals(tagLocalName)) {
                    String key = getAttributeValue(reader, "Key");
                    handlePhysicalAddress(reader, responseItem, key);
                }
            }
        }
    }

    protected void handlePhoneNumbers(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "PhoneNumbers")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Entry".equals(tagLocalName)) {
                    String key = getAttributeValue(reader, "Key");
                    String value = XMLStreamUtil.getElementText(reader);
                    responseItem.put(key, value);
                }
            }
        }
    }

    protected void handleUserCertificate(XMLStreamReader reader, Item responseItem, String contextTagLocalName) throws XMLStreamException {
        boolean firstValueRead = false;
        String certificate = "";
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, contextTagLocalName)) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Base64Binary".equals(tagLocalName)) {
                    reader.next();
                    String value = reader.getText();
                    if ((value != null) && !value.isEmpty()) {
                        if (!firstValueRead) {
                            // Only first certificate value will be read
                            certificate = value;
                        } else {
                            LOGGER.debug("ResolveNames multiple certificates found, tagLocaleName="
                                    + contextTagLocalName + " Certificate [" + value + "] ignored");
                        }
                    }
                }
            }
        }
        responseItem.put(contextTagLocalName, certificate);
    }

    protected void skipTag(XMLStreamReader reader, String tagLocalName) throws XMLStreamException {
        LOGGER.debug("ResolveNames tag parsing skipped. tagLocalName=" + tagLocalName);
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, tagLocalName)) {
            reader.next();
        }
    }

    @Override
    protected void handleEmailAddresses(XMLStreamReader reader, Item responseItem) throws XMLStreamException {
        while (reader.hasNext() && !XMLStreamUtil.isEndTag(reader, "EmailAddresses")) {
            reader.next();
            if (XMLStreamUtil.isStartTag(reader)) {
                String tagLocalName = reader.getLocalName();
                if ("Entry".equals(tagLocalName)) {
                    String value = XMLStreamUtil.getElementText(reader);
                    if (value != null) {
                        if (value.startsWith("smtp:") || value.startsWith("SMTP:")) {
                            value = value.substring(5);
                            // get smtp address only if not already available through Mailbox info
                            responseItem.putIfAbsent("EmailAddress", value);
                        }
                    }
                }
            }
        }
    }

}
