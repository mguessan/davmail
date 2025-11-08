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

import org.apache.http.auth.Credentials;
import org.apache.http.impl.auth.SPNegoScheme;
import org.apache.log4j.Logger;
import org.ietf.jgss.*;

import javax.security.auth.RefreshFailedException;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.security.PrivilegedAction;
import java.security.Security;

/**
 * Override native SPNegoScheme to handle Kerberos.
 * Try to get Kerberos ticket from session, if this fails use callbacks to get credentials from user.
 */
public class DavMailSPNegoScheme extends SPNegoScheme {
    protected static final Logger LOGGER = Logger.getLogger(DavMailSPNegoScheme.class);
    protected static final Object LOCK = new Object();
    protected static final KerberosHelper.KerberosCallbackHandler KERBEROS_CALLBACK_HANDLER;
    private static LoginContext clientLoginContext;

    static {
        // Load Jaas configuration from class
        Security.setProperty("login.configuration.provider", "davmail.http.KerberosLoginConfiguration");
        // Kerberos callback handler singleton
        KERBEROS_CALLBACK_HANDLER = new KerberosHelper.KerberosCallbackHandler();
    }

    public DavMailSPNegoScheme(final boolean stripPort, final boolean useCanonicalHostname) {
        super(stripPort, useCanonicalHostname);
    }

    public DavMailSPNegoScheme(final boolean stripPort) {
        super(stripPort);
    }

    public DavMailSPNegoScheme() {
        super();
    }

    @Override
    protected byte[] generateGSSToken(final byte[] input, final Oid oid, final String authServer, final Credentials credentials) throws GSSException {
        String protocol = "HTTP";

        LOGGER.debug("KerberosHelper.initSecurityContext " + protocol + '@' + authServer + ' ' + input.length + " bytes token");

        synchronized (LOCK) {
            // check cached TGT
            if (clientLoginContext != null) {
                for (KerberosTicket kerberosTicket : clientLoginContext.getSubject().getPrivateCredentials(KerberosTicket.class)) {
                    if (kerberosTicket.getServer().getName().startsWith("krbtgt") && !kerberosTicket.isCurrent()) {
                        LOGGER.debug("KerberosHelper.clientLogin cached TGT expired, try to relogin");
                        clientLoginContext = null;
                    }
                }
            }
            // create client login context
            if (clientLoginContext == null) {
                final LoginContext localLoginContext;
                try {
                    localLoginContext = new LoginContext("spnego-client", KERBEROS_CALLBACK_HANDLER);
                    localLoginContext.login();
                    clientLoginContext = localLoginContext;
                } catch (LoginException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw new GSSException(GSSException.FAILURE);
                }
            }
            // try to renew almost expired tickets
            for (KerberosTicket kerberosTicket : clientLoginContext.getSubject().getPrivateCredentials(KerberosTicket.class)) {
                LOGGER.debug("KerberosHelper.clientLogin ticket for " + kerberosTicket.getServer().getName() + " expires at " + kerberosTicket.getEndTime());
                if (kerberosTicket.getEndTime().getTime() < System.currentTimeMillis() + 10000) {
                    if (kerberosTicket.isRenewable()) {
                        try {
                            kerberosTicket.refresh();
                        } catch (RefreshFailedException e) {
                            LOGGER.debug("KerberosHelper.clientLogin failed to renew ticket " + kerberosTicket);
                        }
                    } else {
                        LOGGER.debug("KerberosHelper.clientLogin ticket is not renewable");
                    }
                }
            }

            Object result = internalGenerateGSSToken(input, oid, authServer, credentials);

            if (result instanceof GSSException) {
                LOGGER.info("KerberosHelper.initSecurityContext exception code " + ((GSSException) result).getMajor() + " minor code " + ((GSSException) result).getMinor() + " message " + ((Throwable) result).getMessage());
                throw (GSSException) result;
            }

            LOGGER.debug("KerberosHelper.initSecurityContext return " + ((byte[]) result).length + " bytes token");
            return (byte[]) result;
        }
    }

    @SuppressWarnings("removal")
    protected Object internalGenerateGSSToken(final byte[] input, final Oid oid, final String authServer, final Credentials credentials) {
        return Subject.doAs(clientLoginContext.getSubject(), (PrivilegedAction<Object>) () -> {
            Object result;
            try {
                result = super.generateGSSToken(input, oid, authServer, credentials);
            } catch (GSSException e) {
                result = e;
            }
            return result;
        });
    }

}
