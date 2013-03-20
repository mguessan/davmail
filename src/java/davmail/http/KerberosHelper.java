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

import davmail.Settings;
import org.apache.log4j.Logger;
import org.ietf.jgss.*;

import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.PrivilegedAction;
import java.security.Security;


/**
 * Kerberos helper class.
 */
public class KerberosHelper {
    protected static final Logger LOGGER = Logger.getLogger(KerberosHelper.class);
    protected static final Object LOCK = new Object();
    protected static final KerberosCallbackHandler KERBEROS_CALLBACK_HANDLER;
    protected static LoginContext clientLoginContext;

    static {
        // Load Jaas configuration from class
        Security.setProperty("login.configuration.provider", "davmail.http.KerberosLoginConfiguration");
        // Kerberos callback handler singleton
        KERBEROS_CALLBACK_HANDLER = new KerberosCallbackHandler();
    }

    protected static class KerberosCallbackHandler implements CallbackHandler {
        String principal;
        String password;

        protected KerberosCallbackHandler() {
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (int i = 0; i < callbacks.length; i++) {
                if (callbacks[i] instanceof NameCallback) {
                    if (principal == null) {
                        // if we get there kerberos token is missing or invalid
                        if (Settings.getBooleanProperty("davmail.server") || GraphicsEnvironment.isHeadless()) {
                            // headless or server mode
                            System.out.print(((NameCallback) callbacks[i]).getPrompt());
                            BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                            principal = inReader.readLine();
                        } else {
                            // TODO: get username and password from dialog
                        }
                    }
                    ((NameCallback) callbacks[i]).setName(principal);

                } else if (callbacks[i] instanceof PasswordCallback) {
                    if (password == null) {
                        // if we get there kerberos token is missing or invalid
                        if (Settings.getBooleanProperty("davmail.server") || GraphicsEnvironment.isHeadless()) {
                            // headless or server mode
                            System.out.print(((PasswordCallback) callbacks[i]).getPrompt());
                            BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
                            password = inReader.readLine();
                        }
                    }
                    ((PasswordCallback) callbacks[i]).setPassword(password.toCharArray());

                } else {
                    throw new UnsupportedCallbackException(callbacks[i]);
                }
            }
        }
    }

    /**
     * Get response Kerberos token for host with provided token.
     *
     * @param host  target host
     * @param token input token
     * @return response token
     * @throws GSSException   on error
     * @throws LoginException on error
     */
    public static byte[] initSecurityContext(final String host, final byte[] token) throws GSSException, LoginException {
        return initSecurityContext(host, null, token);
    }

    /**
     * Get response Kerberos token for host with provided token, use client provided delegation credentials.
     * Used to authenticate with target host on a gateway server with client credentials,
     * gateway must have its own principal authorized for delegation
     *
     * @param host                 target host
     * @param delegatedCredentials client delegated credentials
     * @param token                input token
     * @return response token
     * @throws GSSException   on error
     * @throws LoginException on error
     */
    public static byte[] initSecurityContext(final String host, final GSSCredential delegatedCredentials, final byte[] token) throws GSSException, LoginException {
        LOGGER.debug("KerberosHelper.initSecurityContext " + host + " " + token.length + " bytes token");

        // TODO: handle ticket expiration ?
        // create client login context
        clientLogin();

        Object result = Subject.doAs(clientLoginContext.getSubject(), new PrivilegedAction() {

            public Object run() {
                Object result;
                try {
                    GSSManager manager = GSSManager.getInstance();
                    GSSName serverName = manager.createName("HTTP/" + host, null);
                    // Kerberos v5 OID
                    Oid krb5Oid = new Oid("1.2.840.113554.1.2.2");

                    GSSContext context = manager.createContext(serverName, krb5Oid, delegatedCredentials,
                            GSSContext.DEFAULT_LIFETIME);

                    //context.requestMutualAuth(true);
                    // TODO: used by IIS to pass token to Exchange ?
                    context.requestCredDeleg(true);

                    result = context.initSecContext(token, 0, token.length);
                } catch (GSSException e) {
                    result = e;
                }
                return result;
            }
        });
        if (result instanceof GSSException) {
            // TODO: manage error codes
            LOGGER.info("KerberosHelper.initSecurityContext exception code " + ((GSSException) result).getMajor() + " message" + ((GSSException) result).getMessage());

            throw (GSSException) result;
        }

        LOGGER.debug("KerberosHelper.initSecurityContext return " + ((byte[]) result).length + " bytes token");
        return (byte[]) result;
    }

    /**
     * Create client Kerberos context.
     *
     * @throws LoginException on error
     */
    public static void clientLogin() throws LoginException {
        synchronized (LOCK) {
            if (clientLoginContext == null) {
                final LoginContext localLoginContext = new LoginContext("spnego-client", KERBEROS_CALLBACK_HANDLER);
                localLoginContext.login();
                clientLoginContext = localLoginContext;
            }
        }
    }

    /**
     * Create server side Kerberos login context for provided credentials.
     *
     * @param principal server principal
     * @param password  server passsword
     * @return LoginContext server login context
     * @throws LoginException on error
     */
    public static LoginContext serverLogin(final String principal, final String password) throws LoginException {
        LoginContext serverLoginContext = new LoginContext("spnego-server", new CallbackHandler() {

            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (int i = 0; i < callbacks.length; i++) {
                    if (callbacks[i] instanceof NameCallback) {
                        final NameCallback nameCallback = (NameCallback) callbacks[i];
                        nameCallback.setName(principal);
                    } else if (callbacks[i] instanceof PasswordCallback) {
                        final PasswordCallback passCallback = (PasswordCallback) callbacks[i];
                        passCallback.setPassword(password.toCharArray());
                    } else {
                        throw new UnsupportedCallbackException(callbacks[i]);
                    }
                }

            }
        });
        serverLoginContext.login();
        return serverLoginContext;
    }

    /**
     * Contains server Kerberos context information in server mode.
     */
    public static class SecurityContext {
        /**
         * response token
         */
        public byte[] token;
        /**
         * authenticated principal
         */
        public String principal;
        /**
         * client delegated credential
         */
        public GSSCredential clientCredential;
    }

    /**
     * Check client provided Kerberos token in server login context
     *
     * @param serverLoginContext server login context
     * @param token              Kerberos client token
     * @return result with client principal and optional returned Kerberos token
     * @throws GSSException on error
     */
    public static SecurityContext acceptSecurityContext(LoginContext serverLoginContext, final byte[] token) throws GSSException {
        Object result = Subject.doAs(serverLoginContext.getSubject(), new PrivilegedAction() {

            public Object run() {
                Object result;
                SecurityContext securityContext = new SecurityContext();
                try {
                    GSSManager manager = GSSManager.getInstance();

                    // get server credentials from context
                    Oid krb5oid = new Oid("1.2.840.113554.1.2.2");
                    GSSCredential serverCreds = manager.createCredential(null/* use name from login context*/,
                            GSSCredential.DEFAULT_LIFETIME,
                            krb5oid,
                            GSSCredential.ACCEPT_ONLY/* server mode */);
                    GSSContext context = manager.createContext(serverCreds);

                    securityContext.token = context.acceptSecContext(token, 0, token.length);
                    if (context.isEstablished()) {
                        securityContext.principal = context.getSrcName().toString();
                        LOGGER.debug("Authenticated user: " + securityContext.principal);
                        if (!context.getCredDelegState()) {
                            LOGGER.debug("Credentials can not be delegated");
                        } else {
                            // Get client delegated credentials from context (gateway mode)
                            securityContext.clientCredential = context.getDelegCred();
                        }
                    }
                    result = securityContext;
                } catch (GSSException e) {
                    result = e;
                }
                return result;
            }
        });
        if (result instanceof GSSException) {
            // TODO: manage error codes
            LOGGER.info("KerberosHelper.acceptSecurityContext exception code " + ((GSSException) result).getMajor() + " message" + ((GSSException) result).getMessage());
            throw (GSSException) result;
        }
        return (SecurityContext) result;
    }
}
