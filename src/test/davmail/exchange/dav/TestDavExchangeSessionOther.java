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

package davmail.exchange.dav;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.property.DavProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class TestDavExchangeSessionOther extends AbstractDavExchangeSessionTestCase {


    /**
     * Get main category list
     *
     * @throws IOException on error
     */
    public void testGetCategoryList() throws IOException {
        Set<String> attributes = new HashSet<>();
        attributes.add("permanenturl");
        attributes.add("roamingxmlstream");
        MultiStatusResponse[] responses = davSession.searchItems("/users/" + davSession.getEmail() + "/calendar", attributes, davSession.and(davSession.isFalse("isfolder"), davSession.isEqualTo("messageclass", "IPM.Configuration.CategoryList")), HC4DavExchangeSession.FolderQueryTraversal.Shallow, 0);
        String value = (String) responses[0].getProperties(HttpStatus.SC_OK).get(Field.getPropertyName("roamingxmlstream")).getValue();
        String propertyList = new String(Base64.decodeBase64(value.getBytes()), StandardCharsets.UTF_8);
        System.out.println(propertyList);
    }

    /**
     * Retrieve all hidden items
     *
     * @throws IOException on error
     */
    public void testAllHidden() throws IOException {
        Set<String> attributes = new HashSet<>();
        attributes.add("messageclass");
        attributes.add("permanenturl");
        attributes.add("roamingxmlstream");
        attributes.add("displayname");

        MultiStatusResponse[] responses = davSession.searchItems("/users/" + davSession.getEmail() + '/', attributes, davSession.and(davSession.isTrue("ishidden")), HC4DavExchangeSession.FolderQueryTraversal.Deep, 0);
        for (MultiStatusResponse response : responses) {
            System.out.println(response.getProperties(HttpStatus.SC_OK).get(Field.getPropertyName("messageclass")).getValue() + ": "
                    + response.getProperties(HttpStatus.SC_OK).get(Field.getPropertyName("displayname")).getValue());

            DavProperty<?> roamingxmlstreamProperty = response.getProperties(HttpStatus.SC_OK).get(Field.getPropertyName("roamingxmlstream"));
            if (roamingxmlstreamProperty != null) {
                System.out.println(new String(Base64.decodeBase64(((String) roamingxmlstreamProperty.getValue()).getBytes()), StandardCharsets.UTF_8));
            }

        }
    }

    /**
     * Search in non ipm subtree
     *
     * @throws IOException on error
     */
    public void testNonIpmSubtree() throws IOException {
        Set<String> attributes = new HashSet<>();
        attributes.add("messageclass");
        attributes.add("permanenturl");
        attributes.add("roamingxmlstream");
        attributes.add("roamingdictionary");
        attributes.add("displayname");

        MultiStatusResponse[] responses = davSession.searchItems("/users/" + davSession.getEmail() + "/non_ipm_subtree", attributes, davSession.and(davSession.isTrue("ishidden")), HC4DavExchangeSession.FolderQueryTraversal.Deep, 0);
        for (MultiStatusResponse response : responses) {
            System.out.println(response.getHref() + ' ' + response.getProperties(HttpStatus.SC_OK).get(Field.getPropertyName("messageclass")).getValue() + ": "
                    + response.getProperties(HttpStatus.SC_OK).get(Field.getPropertyName("displayname")).getValue());

            DavProperty<?> roamingxmlstreamProperty = response.getProperties(HttpStatus.SC_OK).get(Field.getPropertyName("roamingxmlstream"));
            if (roamingxmlstreamProperty != null) {
                System.out.println("roamingxmlstream: " + new String(Base64.decodeBase64(((String) roamingxmlstreamProperty.getValue()).getBytes()), StandardCharsets.UTF_8));
            }

            DavProperty<?> roamingdictionaryProperty = response.getProperties(HttpStatus.SC_OK).get(Field.getPropertyName("roamingdictionary"));
            if (roamingdictionaryProperty != null) {
                System.out.println("roamingdictionary: " + new String(Base64.decodeBase64(((String) roamingdictionaryProperty.getValue()).getBytes()), StandardCharsets.UTF_8));
            }
        }
    }

    public void testTimezone() {
        davSession.getVTimezone();
    }

    public void testEncodeAndFixUrl() throws IOException {
        String testUrl = "https://invalid.server.name/space plus+star*dash-slash/ampersand&";
        ((HC4DavExchangeSession)session).restoreHostName = true;
        assertEquals("https://"+server+"/space%20plus%2Bstar*dash-slash/ampersand&",
                ((HC4DavExchangeSession)session).encodeAndFixUrl(testUrl));
    }

}
