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

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.util.HashMap;

/**
 * Custom JAAS login configuration.
 * Equivalent to the following configuration:
 * spnego-client {
 * com.sun.security.auth.module.Krb5LoginModule required useTicketCache=true renewTGT=true;
 * };
 * spnego-server {
 * com.sun.security.auth.module.Krb5LoginModule required isInitiator=false useKeyTab=false storeKey=true;
 * };
 * <p/>
 */
public class KerberosLoginConfiguration extends Configuration {
    protected static final Logger LOGGER = Logger.getLogger(KerberosLoginConfiguration.class);
    protected static final AppConfigurationEntry[] SERVER_LOGIN_MODULE;

    static {
        HashMap<String, String> serverLoginModuleOptions = new HashMap<>();
        if (LOGGER.isDebugEnabled()) {
            serverLoginModuleOptions.put("debug", "true");
        }
        serverLoginModuleOptions.put("isInitiator", "false"); // acceptor (server) mode
        serverLoginModuleOptions.put("useKeyTab", "false"); // do not use credentials stored in keytab file
        serverLoginModuleOptions.put("storeKey", "true"); // store credentials in subject
        SERVER_LOGIN_MODULE = new AppConfigurationEntry[]{new AppConfigurationEntry(
                "com.sun.security.auth.module.Krb5LoginModule",
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                serverLoginModuleOptions)};
    }

    private static AppConfigurationEntry[] buildClientLoginModule(String principal) {
        HashMap<String, String> options = new HashMap<>();
        if (LOGGER.isDebugEnabled()) {
            options.put("debug", "true");
        }
        options.put("useTicketCache", "true");
        options.put("renewTGT", "true");
        String krb5ccName = System.getenv().get("KRB5CCNAME");
        if (krb5ccName != null && !krb5ccName.isEmpty()) {
            options.put("ticketCache", krb5ccName);
        }
        if (principal != null && !principal.isEmpty()) {
            options.put("principal", principal);
        }
        LOGGER.debug("KerberosLoginConfiguration: " + options);
        return new AppConfigurationEntry[]{new AppConfigurationEntry(
                "com.sun.security.auth.module.Krb5LoginModule",
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                options)};
    }

    private final String clientPrincipal;

    public KerberosLoginConfiguration(String clientPrincipal) {
        this.clientPrincipal = clientPrincipal;
    }

    public KerberosLoginConfiguration() {
        this(null);
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        if ("spnego-client".equals(name)) {
            return buildClientLoginModule(clientPrincipal);
        } else if ("spnego-server".equals(name)) {
            return SERVER_LOGIN_MODULE;
        } else {
            return null;
        }
    }

    @Override
    public void refresh() {
        // nothing to do
    }
}