/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2012  Mickael Guessant
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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.auth.*;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.ietf.jgss.GSSException;

import javax.security.auth.login.LoginException;

/**
 * Implement spnego (Negotiate) authentication scheme.
 */
public class SpNegoScheme implements AuthScheme {
    private static final int UNINITIATED = 0;
    private static final int INITIATED = 1;
    private static final int TYPE1_MSG_GENERATED = 2;
    private static final int TYPE2_MSG_RECEIVED = 3;
    private static final int TYPE3_MSG_GENERATED = 4;
    private static final int FAILED = Integer.MAX_VALUE;

    private byte[] serverToken;
    /**
     * Authentication process state
     */
    private int state;

    /**
     * Processes the Negotiate challenge.
     *
     * @param challenge the challenge string
     * @throws MalformedChallengeException is thrown if the authentication challenge is malformed
     */
    public void processChallenge(final String challenge) throws MalformedChallengeException {
        String authScheme = AuthChallengeParser.extractScheme(challenge);
        if (!authScheme.equalsIgnoreCase(getSchemeName())) {
            throw new MalformedChallengeException("Invalid Negotiate challenge: " + challenge);
        }
        int spaceIndex = challenge.indexOf(' ');
        if (spaceIndex != -1) {
            // step 2: received server challenge
            serverToken = Base64.decodeBase64(EncodingUtil.getBytes(
                    challenge.substring(spaceIndex, challenge.length()).trim(), "ASCII"));
            this.state = TYPE2_MSG_RECEIVED;
        } else {
            this.serverToken = null;
            if (this.state == UNINITIATED) {
                this.state = INITIATED;
            } else {
                this.state = FAILED;
            }
        }
    }


    /**
     * Returns textual designation of the Negotiate authentication scheme.
     *
     * @return <code>Negotiate</code>
     */
    public String getSchemeName() {
        return "Negotiate";
    }

    /**
     * Not used with Negotiate.
     *
     * @return null
     */
    public String getParameter(String s) {
        return null;
    }

    /**
     * Not used with Negotiate.
     *
     * @return null
     */
    public String getRealm() {
        return null;
    }

    /**
     * Deprecated.
     */
    @Deprecated
    public String getID() {
        throw new UnsupportedOperationException();
    }

    /**
     * Negotiate is connection based.
     *
     * @return true
     */
    public boolean isConnectionBased() {
        return true;
    }

    /**
     * Tests if the Negotiate authentication process has been completed.
     *
     * @return <tt>true</tt> if authorization has been processed
     */
    public boolean isComplete() {
        return state == TYPE3_MSG_GENERATED || state == FAILED;
    }

    /**
     * Not implemented.
     *
     * @param credentials user credentials
     * @param method      method name
     * @param uri         URI
     * @return an Negotiate authorization string
     * @throws org.apache.commons.httpclient.auth.InvalidCredentialsException
     *          if authentication credentials
     *          are not valid or not applicable for this authentication scheme
     * @throws org.apache.commons.httpclient.auth.AuthenticationException
     *          if authorization string cannot
     *          be generated due to an authentication failure
     */
    @Deprecated
    public String authenticate(final Credentials credentials, String method, String uri) throws AuthenticationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Produces Negotiate authorization string for the given set of
     * {@link Credentials}.
     *
     * @param credentials The set of credentials to be used for authentication
     * @param httpMethod  The method being authenticated
     * @return an Negotiate authorization string
     * @throws org.apache.commons.httpclient.auth.InvalidCredentialsException
     *                                 if authentication credentials
     *                                 are not valid or not applicable for this authentication scheme
     * @throws AuthenticationException if authorization string cannot
     *                                 be generated due to an authentication failure
     */
    public String authenticate(Credentials credentials, HttpMethod httpMethod) throws AuthenticationException {
        if (this.state == UNINITIATED) {
            throw new IllegalStateException("Negotiate authentication process has not been initiated");
        }
        String host = null;
        try {
            host = httpMethod.getURI().getHost();
        } catch (URIException e) {
            // ignore
        }
        if (host == null) {
            Header header = httpMethod.getRequestHeader("Host");
            if (header != null) {
                host = header.getValue();
                if (host.indexOf(':') >= 0) {
                    host = host.substring(0, host.indexOf(':'));
                }
            }
        }
        if (host == null) {
            throw new IllegalStateException("Negotiate authentication failed: empty host");
        }

        // no credentials needed
        String response;
        try {
            if (this.state == INITIATED || this.state == FAILED) {
                // send initial token to server
                response = EncodingUtil.getAsciiString(Base64.encodeBase64(KerberosHelper.initSecurityContext(host, new byte[0])));
                this.state = TYPE1_MSG_GENERATED;
            } else {
                // send challenge response
                response = EncodingUtil.getAsciiString(Base64.encodeBase64(KerberosHelper.initSecurityContext(host, serverToken)));
                this.state = TYPE3_MSG_GENERATED;
            }
        } catch (GSSException gsse) {
            state = FAILED;
            if (gsse.getMajor() == GSSException.DEFECTIVE_CREDENTIAL
                    || gsse.getMajor() == GSSException.CREDENTIALS_EXPIRED)
                throw new InvalidCredentialsException(gsse.getMessage(), gsse);
            if (gsse.getMajor() == GSSException.NO_CRED)
                throw new CredentialsNotAvailableException(gsse.getMessage(), gsse);
            if (gsse.getMajor() == GSSException.DEFECTIVE_TOKEN
                    || gsse.getMajor() == GSSException.DUPLICATE_TOKEN
                    || gsse.getMajor() == GSSException.OLD_TOKEN)
                throw new AuthChallengeException(gsse.getMessage(), gsse);
            // other error
            throw new AuthenticationException(gsse.getMessage(), gsse);
        } catch (LoginException e) {
            state = FAILED;
            throw new InvalidCredentialsException(e.getMessage(), e);
        }
        return "Negotiate " + response;
    }

}
