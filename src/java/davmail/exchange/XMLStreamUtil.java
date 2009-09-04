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
import java.io.IOException;
import java.io.InputStream;
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
}
