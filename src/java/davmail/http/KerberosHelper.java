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

import org.apache.log4j.Logger;
import org.ietf.jgss.*;

import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.security.Security;


/**
 * Kerberos helper class.
 */
public class KerberosHelper {
    protected static final Logger LOGGER = Logger.getLogger(KerberosHelper.class);
    protected static final Object LOCK = new Object();
    protected static KerberosCallbackHandler kerberosCallbackHandler;
    protected static LoginContext loginContext;

    static {
        Security.setProperty("login.configuration.provider", "davmail.http.KerberosLoginConfiguration");
        kerberosCallbackHandler = new KerberosCallbackHandler();
    }

    protected static class KerberosCallbackHandler implements CallbackHandler {
        String principal;
        String password;

        protected KerberosCallbackHandler() {
        }

        protected KerberosCallbackHandler(String principal, String password) {
            this.principal = principal;
            this.password = password;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (int i = 0; i < callbacks.length; i++) {
                if (callbacks[i] instanceof NameCallback) {
                    if (principal == null) {
                        throw new UnsupportedCallbackException(callbacks[i]);
                    }
                    final NameCallback nameCallback = (NameCallback) callbacks[i];
                    nameCallback.setName(principal);
                } else if (callbacks[i] instanceof PasswordCallback) {
                    if (password == null) {
                        throw new UnsupportedCallbackException(callbacks[i]);
                    }
                    final PasswordCallback passCallback = (PasswordCallback) callbacks[i];
                    passCallback.setPassword(password.toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callbacks[i]);
                }
            }
        }
    }

    public static byte[] getToken(final String host, final byte[] token) throws GSSException, LoginException {
        LOGGER.debug("KerberosHelper.getToken " + host + " " + token.length + " bytes token");

        LoginContext loginContext = login();

        Object result = Subject.doAs(loginContext.getSubject(), new PrivilegedAction() {

            public Object run() {
                Object result;
                try {
                    GSSManager manager = GSSManager.getInstance();
                    GSSName serverName = manager.createName("HTTP/" + host, null);
                    // Kerberos v5 OID
                    Oid krb5Oid = new Oid("1.2.840.113554.1.2.2");

                    GSSContext context = manager.createContext(serverName, krb5Oid, null,
                            GSSContext.DEFAULT_LIFETIME);

                    //context.requestMutualAuth(true);
                    context.requestCredDeleg(true);

                    result = context.initSecContext(token, 0, token.length);
                } catch (GSSException e) {
                    result = e;
                }
                return result;
            }
        });
        if (result instanceof GSSException) {
            throw (GSSException) result;
        }

        LOGGER.debug("KerberosHelper.getToken return " + ((byte[]) result).length + " bytes token");
        return (byte[]) result;
    }

    public static void setCredentials(String principal, String password) {
        kerberosCallbackHandler = new KerberosCallbackHandler(principal, password);
    }

    public static LoginContext login() throws LoginException {
        synchronized (LOCK) {
            if (loginContext == null) {
                final LoginContext localLoginContext = new LoginContext("spnego-client", kerberosCallbackHandler);
                localLoginContext.login();
                loginContext = localLoginContext;
            }
        }
        return loginContext;
    }
}
