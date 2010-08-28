/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * XmlStreamReader utility methods
 */
public final class XMLStreamUtil {
    private XMLStreamUtil() {
    }

    /**
     * Build a new XMLInputFactory.
     *
     * @return XML input factory
     */
    public static XMLInputFactory getXmlInputFactory() {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
        return inputFactory;
    }

    /**
     * Convert the XML stream to a map of entries.
     * An entry is also a key/value map
     *
     * @param inputStream xml input stream
     * @param rowName     xml tag name of entries
     * @param idName      xml tag name of entry attribute used as key in the main map
     * @return map of entries
     * @throws IOException on error
     */
    public static Map<String, Map<String, String>> getElementContentsAsMap(InputStream inputStream, String rowName, String idName) throws IOException {
        Map<String, Map<String, String>> results = new HashMap<String, Map<String, String>>();
        Map<String, String> item = null;
        String currentElement = null;
        XMLStreamReader reader = null;
        try {
            XMLInputFactory inputFactory = getXmlInputFactory();
            reader = inputFactory.createXMLStreamReader(inputStream);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && rowName.equals(reader.getLocalName())) {
                    item = new HashMap<String, String>();
                } else if (event == XMLStreamConstants.END_ELEMENT && rowName.equals(reader.getLocalName())) {
                    if (item.containsKey(idName)) {
                        results.put(item.get(idName).toLowerCase(), item);
                    }
                    item = null;
                } else if (event == XMLStreamConstants.START_ELEMENT && item != null) {
                    currentElement = reader.getLocalName();
                } else if (event == XMLStreamConstants.CHARACTERS && currentElement != null) {
                    item.put(currentElement, reader.getText());
                    currentElement = null;
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (XMLStreamException e) {
                ExchangeSession.LOGGER.error(e);
            }
        }
        return results;
    }

    /**
     * Get attribute value for attribute name.
     * reader must be at START_ELEMENT state
     *
     * @param reader        xml stream reader
     * @param attributeName attribute name
     * @return attribute value
     */
    public static String getAttributeValue(XMLStreamReader reader, String attributeName) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (attributeName.equals(reader.getAttributeLocalName(i))) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    /**
     * Test if reader is on a start tag named tagLocalName.
     *
     * @param reader       xml stream reader
     * @param tagLocalName tag local name
     * @return true if reader is on a start tag named tagLocalName
     */
    public static boolean isStartTag(XMLStreamReader reader, String tagLocalName) {
        return (reader.getEventType() == XMLStreamConstants.START_ELEMENT) && (reader.getLocalName().equals(tagLocalName));
    }

    /**
     * Test if reader is on a start tag.
     *
     * @param reader       xml stream reader
     * @return true if reader is on a start tag
     */
    public static boolean isStartTag(XMLStreamReader reader) {
        return (reader.getEventType() == XMLStreamConstants.START_ELEMENT);
    }

    /**
     * Test if reader is on an end tag named tagLocalName.
     *
     * @param reader       xml stream reader
     * @param tagLocalName tag local name
     * @return true if reader is on an end tag named tagLocalName
     */
    public static boolean isEndTag(XMLStreamReader reader, String tagLocalName) {
        return (reader.getEventType() == XMLStreamConstants.END_ELEMENT) && (reader.getLocalName().equals(tagLocalName));
    }

    /**
     * Create XML stream reader for byte array
     *
     * @param xmlContent xml content as byte array
     * @return XML stream reader
     * @throws XMLStreamException on error
     */
    public static XMLStreamReader createXMLStreamReader(byte[] xmlContent) throws XMLStreamException {
        return createXMLStreamReader(new ByteArrayInputStream(xmlContent));
    }

    /**
     * Create XML stream reader for string
     *
     * @param xmlContent xml content as string
     * @return XML stream reader
     * @throws XMLStreamException on error
     */
    public static XMLStreamReader createXMLStreamReader(String xmlContent) throws XMLStreamException {
        XMLInputFactory xmlInputFactory = XMLStreamUtil.getXmlInputFactory();
        return xmlInputFactory.createXMLStreamReader(new StringReader(xmlContent));
    }

    /**
     * Create XML stream reader for inputStream
     *
     * @param inputStream xml content inputStream
     * @return XML stream reader
     * @throws XMLStreamException on error
     */
    public static XMLStreamReader createXMLStreamReader(InputStream inputStream) throws XMLStreamException {
        XMLInputFactory xmlInputFactory = XMLStreamUtil.getXmlInputFactory();
        return xmlInputFactory.createXMLStreamReader(inputStream);
    }

}
