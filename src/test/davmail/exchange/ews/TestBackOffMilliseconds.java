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

import davmail.AbstractDavMailTestCase;
import davmail.exchange.XMLStreamUtil;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TestBackOffMilliseconds extends AbstractDavMailTestCase {

    @Override
    public void setUp() throws IOException {
        super.setUp();

    }

    String errorDescription;
    String errorValue;
    String errorDetail;
    long backOffMilliseconds;

    protected String handleTag(XMLStreamReader reader, String localName) throws XMLStreamException {
        StringBuilder result = null;
        int event = reader.getEventType();
        if (event == XMLStreamConstants.START_ELEMENT && localName.equals(reader.getLocalName())) {
            result = new StringBuilder();
            while (reader.hasNext() &&
                    !((event == XMLStreamConstants.END_ELEMENT && localName.equals(reader.getLocalName())))) {
                event = reader.next();
                if (event == XMLStreamConstants.CHARACTERS) {
                    result.append(reader.getText());
                } else if ("MessageXml".equals(localName) && event == XMLStreamConstants.START_ELEMENT) {
                    String attributeValue = null;
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        if (result.length() > 0) {
                            result.append(", ");
                        }
                        attributeValue = reader.getAttributeValue(i);
                        result.append(reader.getAttributeLocalName(i)).append(": ").append(reader.getAttributeValue(i));
                    }
                    // catch BackOffMilliseconds value
                    if ("BackOffMilliseconds".equals(attributeValue)) {
                        try {
                            backOffMilliseconds = Long.parseLong(reader.getElementText());
                        } catch (NumberFormatException e) {
                            //LOGGER.error(e, e.getMessage());
                        }
                    }
                }
            }
        }
        if (result != null && result.length() > 0) {
            return result.toString();
        } else {
            return null;
        }
    }

    protected void handleErrors(XMLStreamReader reader) throws XMLStreamException {
        String result = handleTag(reader, "ResponseCode");
        // store error description;
        String messageText = handleTag(reader, "MessageText");
        if (messageText != null) {
            errorDescription = messageText;
        }
        String messageXml = handleTag(reader, "MessageXml");
        if (messageXml != null) {
            // contains BackOffMilliseconds on ErrorServerBusy
            errorValue = messageXml;
        }
        if (errorDetail == null && result != null
                && !"NoError".equals(result)
                && !"ErrorNameResolutionMultipleResults".equals(result)
                && !"ErrorNameResolutionNoResults".equals(result)
                && !"ErrorFolderExists".equals(result)
        ) {
            errorDetail = result;
        }
        if (XMLStreamUtil.isStartTag(reader, "faultstring")) {
            errorDetail = XMLStreamUtil.getElementText(reader);
        }
    }


    public void testBackOffMillisecondsParsing() throws XMLStreamException {
        String content = "<s:Envelope\n" +
                "    xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "  <s:Body>\n" +
                "    <s:Fault>\n" +
                "      <faultcode\n" +
                "    xmlns:a=\"http://schemas.microsoft.com/exchange/services/2006/types\">a:ErrorServerBusy</faultcode>\n" +
                "      <faultstring xml:lang=\"en-US\">The server cannot service this request right now. Try again later.</faultstring>\n" +
                "      <detail>\n" +
                "        <e:ResponseCode\n" +
                "    xmlns:e=\"http://schemas.microsoft.com/exchange/services/2006/errors\">ErrorServerBusy</e:ResponseCode>\n" +
                "        <e:Message\n" +
                "    xmlns:e=\"http://schemas.microsoft.com/exchange/services/2006/errors\">The server cannot service this request right now. Try again later.</e:Message>\n" +
                "        <t:MessageXml\n" +
                "    xmlns:t=\"http://schemas.microsoft.com/exchange/services/2006/types\">\n" +
                "          <t:Value Name=\"BackOffMilliseconds\">297749</t:Value>\n" +
                "        </t:MessageXml>\n" +
                "      </detail>\n" +
                "    </s:Fault>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        XMLStreamReader reader;
        try {
            reader = XMLStreamUtil.createXMLStreamReader(inputStream);
            while (reader.hasNext()) {
                reader.next();
                handleErrors(reader);
            }
        } catch (XMLStreamException e) {
            throw e;
        }
        assertEquals(backOffMilliseconds, 297749);
    }

}
