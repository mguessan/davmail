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
package davmail.ldap;

import davmail.AbstractDavMailTestCase;
import davmail.DavGateway;
import davmail.Settings;
import davmail.exchange.ExchangeSessionFactory;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import java.io.IOException;
import java.util.Hashtable;

/**
 * Test LDAP.
 */
public class TestLdap extends AbstractDavMailTestCase {
    InitialLdapContext ldapContext;

    @Override
    public void setUp() throws IOException {
        super.setUp();
        if (ldapContext == null) {
            // start gateway
            DavGateway.start();
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put("java.naming.security.authentication", "simple");
            env.put("java.naming.security.principal", Settings.getProperty("davmail.username"));
            env.put("java.naming.security.credentials", Settings.getProperty("davmail.password"));

            env.put("com.sun.jndi.ldap.connect.pool", "true");
            env.put("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory");
            env.put("java.naming.provider.url", "ldap://localhost:" + Settings.getIntProperty("davmail.ldapPort"));
            env.put("java.naming.referral", "follow");

            try {
                ldapContext = new InitialLdapContext(env, null);
            } catch (NamingException e) {
                throw new IOException(e);
            }
        }
        if (session == null) {
            session = ExchangeSessionFactory.getInstance(Settings.getProperty("davmail.username"), Settings.getProperty("davmail.password"));
        }
    }

    public void testSearchOneLevel() throws NamingException {
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("ou=people", "(objectclass=*)", searchControls);
    }

    public void testSearchMail() throws NamingException {
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        searchControls.setReturningAttributes(new String[]{"mail"});
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("ou=people", "(objectclass=*)", searchControls);
    }

    public void testMozillaSearchAttributes() throws NamingException {
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        searchControls.setReturningAttributes(new String[]{"custom1", "mozillausehtmlmail", "postalcode", "custom2", "custom3", "custom4", "street", "surname", "telephonenumber", "mozillahomelocalityname", "orgunit", "mozillaworkstreet2", "xmozillanickname", "mozillahomestreet", "description", "cellphone", "homeurl", "mozillahomepostalcode", "departmentnumber", "postofficebox", "st", "objectclass", "sn", "ou", "fax", "mozillahomeurl", "mozillahomecountryname", "streetaddress", "cn", "company", "mozillaworkurl", "mobile", "region", "birthmonth", "birthday", "labeleduri", "carphone", "department", "xmozillausehtmlmail", "givenname", "nsaimid", "workurl", "facsimiletelephonenumber", "mozillanickname", "title", "nscpaimscreenname", "xmozillasecondemail", "mozillacustom3", "countryname", "mozillacustom4", "mozillacustom1", "mozillacustom2", "homephone", "mozillasecondemail", "pager", "zip", "mail", "c", "mozillahomestate", "o", "l", "birthyear", "modifytimestamp", "locality", "commonname", "notes", "pagerphone", "mozillahomestreet2"});
        NamingEnumeration<SearchResult> searchResults = ldapContext.search("ou=people", "(objectclass=*)", searchControls);
    }
}
