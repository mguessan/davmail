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

import jcifs.ntlmssp.NtlmFlags;
import jcifs.ntlmssp.Type1Message;
import jcifs.ntlmssp.Type2Message;
import jcifs.ntlmssp.Type3Message;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.auth.*;
import org.apache.commons.httpclient.util.EncodingUtil;

import java.io.IOException;

/**
 * NTLMv2 scheme implementation.
 */
public class NTLMv2Scheme implements AuthScheme {
    private static final int UNINITIATED = 0;
    private static final int INITIATED = 1;
    private static final int TYPE1_MSG_GENERATED = 2;
    private static final int TYPE2_MSG_RECEIVED = 3;
    private static final int TYPE3_MSG_GENERATED = 4;
    private static final int FAILED = Integer.MAX_VALUE;

    private Type2Message type2Message;
    /**
     * Authentication process state
     */
    private int state;

    /**
     * Processes the NTLM challenge.
     *
     * @param challenge the challenge string
     * @throws MalformedChallengeException is thrown if the authentication challenge
     *                                     is malformed
     */
    public void processChallenge(final String challenge) throws MalformedChallengeException {
        String authScheme = AuthChallengeParser.extractScheme(challenge);
        if (!authScheme.equalsIgnoreCase(getSchemeName())) {
            throw new MalformedChallengeException("Invalid NTLM challenge: " + challenge);
        }
        int spaceIndex = challenge.indexOf(' ');
        if (spaceIndex != -1) {
            try {
                type2Message = new Type2Message(Base64.decodeBase64(EncodingUtil.getBytes(
                        challenge.substring(spaceIndex, challenge.length()).trim(), "ASCII")));
            } catch (IOException e) {
                throw new MalformedChallengeException("Invalid NTLM challenge: " + challenge, e);
            }
            this.state = TYPE2_MSG_RECEIVED;
        } else {
            this.type2Message = null;
            if (this.state == UNINITIATED) {
                this.state = INITIATED;
            } else {
                this.state = FAILED;
            }
        }
    }


    /**
     * Returns textual designation of the NTLM authentication scheme.
     *
     * @return <code>ntlm</code>
     */
    public String getSchemeName() {
        return "ntlm";
    }

    /**
     * Not used with NTLM.
     *
     * @return null
     */
    public String getParameter(String s) {
        return null;
    }

    /**
     * Not used with NTLM.
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
     * NTLM is connection based.
     *
     * @return true
     */
    public boolean isConnectionBased() {
        return true;
    }

    /**
     * Tests if the NTLM authentication process has been completed.
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
     * @return an NTLM authorization string
     * @throws InvalidCredentialsException if authentication credentials
     *                                     are not valid or not applicable for this authentication scheme
     * @throws AuthenticationException     if authorization string cannot
     *                                     be generated due to an authentication failure
     */
    @Deprecated
    public String authenticate(final Credentials credentials, String method, String uri) throws AuthenticationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Produces NTLM authorization string for the given set of
     * {@link Credentials}.
     *
     * @param credentials The set of credentials to be used for authentication
     * @param httpMethod  The method being authenticated
     * @return an NTLM authorization string
     * @throws InvalidCredentialsException if authentication credentials
     *                                     are not valid or not applicable for this authentication scheme
     * @throws AuthenticationException     if authorization string cannot
     *                                     be generated due to an authentication failure
     */
    public String authenticate(Credentials credentials, HttpMethod httpMethod) throws AuthenticationException {
        if (this.state == UNINITIATED) {
            throw new IllegalStateException("NTLM authentication process has not been initiated");
        }

        NTCredentials ntcredentials;
        try {
            ntcredentials = (NTCredentials) credentials;
        } catch (ClassCastException e) {
            throw new InvalidCredentialsException(
                    "Credentials cannot be used for NTLM authentication: "
                            + credentials.getClass().getName());
        }
        String response;
        if (this.state == INITIATED || this.state == FAILED) {
            int flags = NtlmFlags.NTLMSSP_NEGOTIATE_NTLM2 | NtlmFlags.NTLMSSP_NEGOTIATE_ALWAYS_SIGN |
                    NtlmFlags.NTLMSSP_NEGOTIATE_OEM_WORKSTATION_SUPPLIED | NtlmFlags.NTLMSSP_NEGOTIATE_OEM_DOMAIN_SUPPLIED |
                    NtlmFlags.NTLMSSP_NEGOTIATE_NTLM | NtlmFlags.NTLMSSP_REQUEST_TARGET |
                    NtlmFlags.NTLMSSP_NEGOTIATE_OEM | NtlmFlags.NTLMSSP_NEGOTIATE_UNICODE;
            Type1Message type1Message = new Type1Message(flags, ntcredentials.getDomain(), ntcredentials.getHost());
            response = EncodingUtil.getAsciiString(Base64.encodeBase64(type1Message.toByteArray()));
            this.state = TYPE1_MSG_GENERATED;
        } else {
            Type3Message type3Message = new Type3Message(type2Message, ntcredentials.getPassword(),
                    ntcredentials.getDomain(), ntcredentials.getUserName(), ntcredentials.getHost(), 0);
            response = EncodingUtil.getAsciiString(Base64.encodeBase64(type3Message.toByteArray()));
            this.state = TYPE3_MSG_GENERATED;
        }
        return "NTLM " + response;
    }


}
