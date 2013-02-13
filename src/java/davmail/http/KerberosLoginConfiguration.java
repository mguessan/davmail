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
 * com.sun.security.auth.module.Krb5LoginModule required;
 * };
 * <p/>
 */
public class KerberosLoginConfiguration extends Configuration {
    protected static final Logger LOGGER = Logger.getLogger(KerberosHelper.class);
    protected static final AppConfigurationEntry[] CLIENT_LOGIN_MODULE;

    static {
        HashMap<String, String> loginModuleOptions = new HashMap<String, String>();
        if (LOGGER.isDebugEnabled()) {
            loginModuleOptions.put("debug", "true");
        }

        loginModuleOptions.put("useTicketCache", "true");
        //loginModuleOptions.put("doNotPrompt", "true");
        //loginModuleOptions.put("ticketCache", FileCredentialsCache.getDefaultCacheName());
        //loginModuleOptions.put("refreshKrb5Config", "true");
        //loginModuleOptions.put("storeKey", "true");
        CLIENT_LOGIN_MODULE = new AppConfigurationEntry[]{new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule", AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, loginModuleOptions)};

    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
        if ("spnego-client".equals(name)) {
            return CLIENT_LOGIN_MODULE;
        } else {
            return null;
        }
    }

    public void refresh() {
        // nothing to do
    }
}