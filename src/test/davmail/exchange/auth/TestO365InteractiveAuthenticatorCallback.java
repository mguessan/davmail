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
package davmail.exchange.auth;

import junit.framework.TestCase;

public class TestO365InteractiveAuthenticatorCallback extends TestCase {
    private static final String REDIRECT_URI = "https://localhost/common/oauth2/nativeclient";

    public void testCodeBeforeAndAfterOtherParameters() {
        assertCode("?code=opaque-code&state=state-value&session_state=session-value", "opaque-code");
        assertCode("?session_state=session-value&state=state-value&code=opaque-code", "opaque-code");
        assertCode("?code=opaque-code&client_info=info&state=state-value#fragment", "opaque-code");
    }

    public void testDecodesCodeOnce() {
        assertCode("?code=synthetic%2Bvalue%2F%3D%252B&state=something%26code%3Dignored", "synthetic+value/=%2B");
    }

    public void testPreservesErrorWithoutUnrelatedParameters() {
        O365InteractiveAuthenticator authenticator = new O365InteractiveAuthenticator();
        authenticator.handleCode(REDIRECT_URI + "?error_description=Second+factor+required&error=access_denied&state=code=ignored");
        assertFalse(authenticator.isAuthenticated);
        assertNull(authenticator.code);
        assertEquals("error=access_denied&error_description=Second+factor+required", authenticator.errorCode);
    }

    public void testRejectsMissingEmptyAndMalformedCode() {
        assertFailed("?other_code=ignored");
        assertFailed("?state=code%3Dignored");
        assertFailed("?code=");
        assertFailed("?code");
        assertFailed("#code=ignored");
        assertFailed("?code=%XX");
        assertFailed("?code=first&code=second");
    }

    public void testKeepsFirstCallbackResult() {
        O365InteractiveAuthenticator successful = new O365InteractiveAuthenticator();
        successful.handleCode(REDIRECT_URI + "?code=first");
        successful.handleCode(REDIRECT_URI + "?code=second");
        successful.handleCode(REDIRECT_URI + "?error=access_denied");
        assertTrue(successful.isAuthenticated);
        assertEquals("first", successful.code);
        assertNull(successful.errorCode);

        O365InteractiveAuthenticator failed = new O365InteractiveAuthenticator();
        failed.handleCode(REDIRECT_URI + "?error=access_denied");
        failed.handleCode(REDIRECT_URI + "?error=server_error");
        failed.handleCode(REDIRECT_URI + "?code=ignored");
        assertFalse(failed.isAuthenticated);
        assertNull(failed.code);
        assertEquals("error=access_denied", failed.errorCode);
    }

    private void assertCode(String parameters, String expected) {
        O365InteractiveAuthenticator authenticator = new O365InteractiveAuthenticator();
        authenticator.handleCode(REDIRECT_URI + parameters);
        assertTrue(authenticator.isAuthenticated);
        assertEquals(expected, authenticator.code);
        assertNull(authenticator.errorCode);
    }

    private void assertFailed(String parameters) {
        O365InteractiveAuthenticator authenticator = new O365InteractiveAuthenticator();
        authenticator.handleCode(REDIRECT_URI + parameters);
        assertFalse(authenticator.isAuthenticated);
        assertNull(authenticator.code);
        assertNotNull(authenticator.errorCode);
    }
}
