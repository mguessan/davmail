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

package davmail.http;

import junit.framework.TestCase;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.http.Consts;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * A few URIBuilder test cases to replace URIUtil
 */
public class TestURIBuilder extends TestCase {
    public void testEncodeParams() throws URISyntaxException {
        String clientId = "facd6cff-a294-4415-b59f-c5b01937d7bd";
        String redirectUri = "https://login.microsoftonline.com/common/oauth2/nativeclient";
        String resource = "https://outlook.office365.com";
        String username = "domain\\userid|user@company.com";

        String url = "https://login.microsoftonline.com/common/oauth2/authorize"
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&redirect_uri=" + URIUtil.encodeWithinQuery(redirectUri)
                + "&response_mode=query"
                + "&resource=" + URIUtil.encodeWithinQuery(resource)
                + "&login_hint=" + URIUtil.encodeWithinQuery(username);
        URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("login.microsoftonline.com")
                .setPath("/common/oauth2/authorize")
                .addParameter("client_id", clientId)
                .addParameter("response_type", "code")
                .addParameter("redirect_uri", redirectUri)
                .addParameter("response_mode", "query")
                .addParameter("resource", resource)
                .addParameter("login_hint", username)
        .build();
        System.out.println(url);
        assertEquals(uri.toString(), url);
    }

    public void testEncodePath() throws URISyntaxException {
        String url = "https://host"+URIUtil.encodePath("/path with space");
        URI uri = new URIBuilder()
                .setScheme("https")
                .setHost("host")
                .setPath("/path with space")
                .build();
        System.out.println(url);
        assertEquals(uri.toString(), url);
    }

    public void testDecodePlus() throws IOException, URISyntaxException, DecoderException {
        URI uri = new URI("https://host/encoded+plus");
        System.out.println(uri.getPath());
        System.out.println(URIUtil.decode(uri.getPath()));

        String decoded = new String(URLCodec.decodeUrl(uri.getPath().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        assertEquals(decoded, URIUtil.decode(uri.getPath()));
    }

    public void testDecodeSpecial() throws IOException, URISyntaxException, DecoderException {
        URI uri = new URI("https://host/@");
        System.out.println(uri.getPath());
        System.out.println(URIUtil.decode(uri.getPath()));

        String decoded = new String(URLCodec.decodeUrl(uri.getPath().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        assertEquals(decoded, URIUtil.decode(uri.getPath()));
    }

    public void testEncodeSpecial() {
        BitSet ical_allowed_abs_path = new BitSet(256);

        ical_allowed_abs_path.or(org.apache.commons.httpclient.URI.allowed_abs_path);
        ical_allowed_abs_path.clear('@');

        String path = "user@company.com";
        String encoded = URIUtil.encode(path, ical_allowed_abs_path);

        System.out.println(encoded);

        String newEncoded = new String(URLCodec.encodeUrl(ical_allowed_abs_path, path.getBytes(Consts.UTF_8)), Consts.UTF_8);
        System.out.println(newEncoded);

        assertEquals(newEncoded, encoded);
    }







}
