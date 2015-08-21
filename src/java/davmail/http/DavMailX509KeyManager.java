/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2011  Mickael Guessant
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

import davmail.ui.SelectCertificateDialog;
import org.apache.log4j.Logger;

import javax.net.ssl.X509KeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Special X509 Key Manager that handles cases where more than one private key
 * is sufficient to establish the HTTPs connection by asking the user to
 * select one.
 */
public class DavMailX509KeyManager implements X509KeyManager {

    protected static final Logger LOGGER = Logger.getLogger(DavMailX509KeyManager.class);

    // Wrap an existing key manager to handle most of the interface as a pass through
    private final X509KeyManager keyManager;

    // Remember selected alias so we don't continually bug the user
    private String cachedAlias;

    /**
     * Build the specialized key manager wrapping the default one
     *
     * @param keyManager original key manager
     */
    public DavMailX509KeyManager(X509KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    /**
     * Get the client aliases, simply pass this through to wrapped key manager
     */
    public String[] getClientAliases(String string, Principal[] principals) {
        return keyManager.getClientAliases(string, principals);
    }

    /**
     * Select a client alias. Some servers are misconfigured and claim to accept
     * any client certificate during the SSL handshake, however OWA only authenticates
     * using a single certificate.
     * <p/>
     * This method allows the user to select the right client certificate
     */
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        LOGGER.debug("Find client certificates issued by: " + Arrays.asList(issuers));
        // Build a list of all aliases
        ArrayList<String> aliases = new ArrayList<String>();
        for (String keyTypeValue : keyType) {
            String[] keyAliases = keyManager.getClientAliases(keyTypeValue, null);

            if (keyAliases != null) {
                aliases.addAll(Arrays.asList(keyAliases));
            }
        }

        // If there are more than one show a dialog and return the selected alias
        if (aliases.size() > 1) {

            //If there's a saved pattern try to match it
            if (cachedAlias != null) {
                for (String alias : aliases) {
                    if (cachedAlias.equals(stripAlias(alias))) {
                        LOGGER.debug(alias + " matched cached alias: " + cachedAlias);
                        return alias;
                    }
                }

                // pattern didn't match, clear the pattern and ask user to select an alias
                cachedAlias = null;
            }

            String[] aliasesArray = aliases.toArray(new String[aliases.size()]);
            String[] descriptionsArray = new String[aliasesArray.length];
            int i = 0;
            for (String alias : aliasesArray) {
                X509Certificate certificate = getCertificateChain(alias)[0];
                String subject = certificate.getSubjectDN().getName();
                if (subject.contains("=")) {
                    subject = subject.substring(subject.indexOf("=")+1);
                }
                if (subject.contains(",")) {
                    subject = subject.substring(0, subject.indexOf(","));
                }
                try {
                    for (List<?> subjectAltName:certificate.getSubjectAlternativeNames()) {
                        if (subjectAltName.get(1) instanceof String) {
                            subject = " " + subjectAltName.get(1);
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                String issuer = certificate.getIssuerDN().getName();
                if (issuer.contains("=")) {
                    issuer = issuer.substring(issuer.indexOf("=")+1);
                }
                if (issuer.contains(",")) {
                    issuer = issuer.substring(0, issuer.indexOf(","));
                }
                descriptionsArray[i++] = subject + " [" + issuer + "]";
            }
            SelectCertificateDialog selectCertificateDialog = new SelectCertificateDialog(aliasesArray, descriptionsArray);

            LOGGER.debug("User selected Key Alias: " + selectCertificateDialog.getSelectedAlias());

            cachedAlias = stripAlias(selectCertificateDialog.getSelectedAlias());
            LOGGER.debug("Stored Key Alias Pattern: " + cachedAlias);

            return selectCertificateDialog.getSelectedAlias();

            // exactly one, simply return that and don't bother the user
        } else if (aliases.size() == 1) {
            LOGGER.debug("One Private Key found, returning that");
            return aliases.get(0);

            // none, return null
        } else {
            LOGGER.debug("No Private Keys found");
            return null;
        }
    }

    /**
     * PKCS11 aliases are in the format: dd.0, dd is incremented
     * every time the SSL connection is re-negotiated
     *
     * @param alias original alias
     * @return alias without prefix
     */
    protected String stripAlias(String alias) {
        String value = alias;
        if (value != null && value.length() > 1) {
            char firstChar = value.charAt(0);
            int dotIndex = value.indexOf('.'); 
            if (firstChar >= '0' && firstChar <= '9' && dotIndex >= 0) {
                value = value.substring(dotIndex+1);
            }
        }
        return value;
    }

    /**
     * Passthrough to wrapped keymanager
     */
    public String[] getServerAliases(String string, Principal[] prncpls) {
        return keyManager.getServerAliases(string, prncpls);
    }

    /**
     * Passthrough to wrapped keymanager
     */
    public String chooseServerAlias(String string, Principal[] prncpls, Socket socket) {
        return keyManager.chooseServerAlias(string, prncpls, socket);
    }

    /**
     * Passthrough to wrapped keymanager
     */
    public X509Certificate[] getCertificateChain(String string) {
        X509Certificate[] certificates = keyManager.getCertificateChain(string);
        for (X509Certificate certificate: certificates) {
            LOGGER.debug("Certificate chain: " + certificate.getSubjectDN());
        }
        return certificates;
    }

    /**
     * Passthrough to wrapped keymanager
     */
    public PrivateKey getPrivateKey(String string) {
        return keyManager.getPrivateKey(string);
    }
}
