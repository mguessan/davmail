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

package davmail.util;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Create a server socket listener
 */
public class ServerSocketRunner {
    public static void main(String[] argv) throws NoSuchAlgorithmException, KeyManagementException, IOException, KeyStoreException, CertificateException, UnrecoverableKeyException {
        // SSL debug levels
        //System.setProperty("javax.net.debug", "ssl,handshake");
        System.setProperty("javax.net.debug", "all");

        // local truststore
        System.setProperty("javax.net.ssl.trustStore", "cacerts");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");

        // access windows client certificates
        //System.setProperty("javax.net.ssl.trustStoreProvider", "SunMSCAPI");
        //System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");

        // load default trustmanager factory
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        System.out.println(trustManagerFactory.getProvider());

        // load server keystore
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        try(FileInputStream keyStoreInputStream = new FileInputStream("davmail.p12")) {
            keystore.load(keyStoreInputStream, "password".toCharArray());
        }

        // KeyManagerFactory to create key managers
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

        // initialize KMF to work with keystore
        kmf.init(keystore, "password".toCharArray());

        // SSLContext is environment for implementing JSSE...
        // create ServerSocketFactory
        SSLContext sslContext = SSLContext.getInstance("TLS");

        // initialize sslContext to work with key managers and default trust manager
        sslContext.init(kmf.getKeyManagers(), null, null);

        // create ServerSocketFactory from sslContext
        ServerSocketFactory serverSocketFactory = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) serverSocketFactory.createServerSocket(443);
        serverSocket.setNeedClientAuth(true);
        int count = 100;
        while (count-- > 0) {
            SSLSocket socket = (SSLSocket) serverSocket.accept();
            SSLSession session = socket.getSession();
            System.out.println("SubjectDN " + ((X509Certificate) session.getPeerCertificates()[0]).getSubjectDN());
        }
    }
}
