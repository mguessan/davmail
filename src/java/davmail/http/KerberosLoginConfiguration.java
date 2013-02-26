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
    protected static final Logger LOGGER = Logger.getLogger(KerberosHelper.class);
    protected static final AppConfigurationEntry[] CLIENT_LOGIN_MODULE;
    protected static final AppConfigurationEntry[] SERVER_LOGIN_MODULE;

    static {
        HashMap<String, String> clientLoginModuleOptions = new HashMap<String, String>();
        if (LOGGER.isDebugEnabled()) {
            clientLoginModuleOptions.put("debug", "true");
        }

        clientLoginModuleOptions.put("useTicketCache", "true");
        clientLoginModuleOptions.put("renewTGT", "true");
        //clientLoginModuleOptions.put("doNotPrompt", "true");
        //clientLoginModuleOptions.put("ticketCache", FileCredentialsCache.getDefaultCacheName());
        //clientLoginModuleOptions.put("refreshKrb5Config", "true");
        //clientLoginModuleOptions.put("storeKey", "true");
        CLIENT_LOGIN_MODULE = new AppConfigurationEntry[]{new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, clientLoginModuleOptions)};

        HashMap<String, String> serverLoginModuleOptions = new HashMap<String, String>();
        if (LOGGER.isDebugEnabled()) {
            serverLoginModuleOptions.put("debug", "true");
        }

        serverLoginModuleOptions.put("isInitiator", "false"); // acceptor (server) mode
        serverLoginModuleOptions.put("useKeyTab", "false"); // do not use credentials stored in keytab file
        serverLoginModuleOptions.put("storeKey", "true"); // store credentials in subject
        SERVER_LOGIN_MODULE = new AppConfigurationEntry[]{new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, serverLoginModuleOptions)};
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        if ("spnego-client".equals(name)) {
            return CLIENT_LOGIN_MODULE;
        } else if ("spnego-server".equals(name)) {
            return SERVER_LOGIN_MODULE;
        } else {
            return null;
        }
    }

    public void refresh() {
        // nothing to do
    }
}