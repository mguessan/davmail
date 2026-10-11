/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2024  Mickael Guessant
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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Self-signed PKCS12 certificate generator for DavMail SSL.
 * Uses reflection to access sun.security.x509.* internal APIs
 * for compatibility across Java 8, 11, and 17+.
 * <p>
 * Usage on Java 11+:
 * <pre>java --add-exports java.base/sun.security.x509=ALL-UNNAMED -cp davmail.jar davmail.DavGateway -generateCert</pre>
 * On Java 8, the --add-exports flag is not needed.
 */
public class SelfSignedCertificateGenerator {
    private static final Logger LOGGER = Logger.getLogger(SelfSignedCertificateGenerator.class);

    private static final String CERTIFICATE_SIGNATURE_ALGORITHM = "SHA384withRSA";
    private static final int RSA_KEY_LENGTH = 2048;
    private static final int CERTIFICATE_VALIDITY_DAYS = 3650;
    private static final String KEY_ALIAS = "davmail";
    public static final String DEFAULT_KEYSTORE_PASSWORD = "password"; // NOSONAR default keystore password
    private static final String DEFAULT_KEYSTORE_FILENAME = "davmail.p12";

    private SelfSignedCertificateGenerator() {
        // utility class
    }

    /**
     * Resolve keystore path: place davmail.p12 next to the given config file.
     *
     * @param configFilePath path to the DavMail properties file
     * @return resolved keystore file path
     */
    public static String resolveKeystorePath(String configFilePath) {
        if (configFilePath != null && !configFilePath.isEmpty()) {
            File configFile = new File(configFilePath);
            File configDir = configFile.getParentFile();
            if (configDir != null) {
                return new File(configDir, DEFAULT_KEYSTORE_FILENAME).getPath();
            }
        }
        return DEFAULT_KEYSTORE_FILENAME;
    }

    /**
     * Generate a self-signed certificate, save it next to the DavMail settings file,
     * and update settings with the keystore configuration.
     * Called from {@link davmail.DavGateway} with {@code -generateCert} option.
     *
     * @param configFilePath path to the DavMail properties file
     */
    public static void generate(String configFilePath) {
        String keystorePath = resolveKeystorePath(configFilePath);

        try {
            generateAndSave(keystorePath, DEFAULT_KEYSTORE_PASSWORD);

            // Update DavMail settings
            Settings.setProperty("davmail.ssl.keystoreType", "PKCS12");
            Settings.setProperty("davmail.ssl.keystoreFile", keystorePath);
            Settings.setProperty("davmail.ssl.keystorePass", DEFAULT_KEYSTORE_PASSWORD);
            Settings.setProperty("davmail.ssl.keyPass", DEFAULT_KEYSTORE_PASSWORD);
            Settings.save();

            LOGGER.info("Generated self-signed certificate: " + keystorePath);
            LOGGER.info("DavMail settings updated");
        } catch (GeneralSecurityException | IOException e) {
            LOGGER.error("Failed to generate certificate: " + e.getMessage(), e);
            LOGGER.error("On Java 11+, add: --add-exports java.base/sun.security.x509=ALL-UNNAMED");
        }
    }

    /**
     * Generate a self-signed PKCS12 keystore file.
     *
     * @param keystoreFilename output filename (e.g., "davmail.p12")
     * @param keystorePassword password for the keystore and key entry
     * @throws GeneralSecurityException if certificate generation fails
     * @throws IOException              if keystore file cannot be written
     */
    public static void generateAndSave(String keystoreFilename, String keystorePassword) throws GeneralSecurityException, IOException {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            hostname = "localhost";
        }
        String cnString = "cn=" + hostname + ",ou=davmail";

        LOGGER.info("Generating self-signed certificate for " + hostname);

        // Generate RSA key pair (standard API, no reflection needed)
        SecureRandom secureRandom = new SecureRandom();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(RSA_KEY_LENGTH, secureRandom);
        KeyPair pair = keyGen.generateKeyPair();
        PublicKey publicKey = pair.getPublic();
        PrivateKey privateKey = pair.getPrivate();

        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime() + (long) CERTIFICATE_VALIDITY_DAYS * 24 * 3600 * 1000);

        // All sun.security.x509 classes accessed via reflection
        X509Certificate cert = createSelfSignedCertificate(
                cnString, publicKey, privateKey, startDate, endDate, secureRandom);

        // Create PKCS12 keystore and store to file
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(KEY_ALIAS, privateKey, keystorePassword.toCharArray(),
                new X509Certificate[]{cert});

        try (OutputStream os = Files.newOutputStream(Paths.get(keystoreFilename))) {
            keyStore.store(os, keystorePassword.toCharArray());
        }

        LOGGER.info("Self-signed certificate saved to " + keystoreFilename);
    }

    private static X509Certificate createSelfSignedCertificate(
            String cnString, PublicKey publicKey, PrivateKey privateKey,
            Date startDate, Date endDate, SecureRandom secureRandom) throws GeneralSecurityException {
        try {
            // Load classes via reflection
            Class<?> x509CertInfoClass = Class.forName("sun.security.x509.X509CertInfo");
            Class<?> x509CertImplClass = Class.forName("sun.security.x509.X509CertImpl");
            Class<?> certificateVersionClass = Class.forName("sun.security.x509.CertificateVersion");
            Class<?> certificateSerialNumberClass = Class.forName("sun.security.x509.CertificateSerialNumber");
            Class<?> algorithmIdClass = Class.forName("sun.security.x509.AlgorithmId");
            Class<?> certificateAlgorithmIdClass = Class.forName("sun.security.x509.CertificateAlgorithmId");
            Class<?> x500NameClass = Class.forName("sun.security.x509.X500Name");
            Class<?> certificateX509KeyClass = Class.forName("sun.security.x509.CertificateX509Key");
            Class<?> certificateValidityClass = Class.forName("sun.security.x509.CertificateValidity");

            // Create X509CertInfo
            Object info = x509CertInfoClass.getDeclaredConstructor().newInstance();

            // CertificateVersion.V3 = 2
            Object certVersion = certificateVersionClass.getDeclaredConstructor(int.class).newInstance(2);

            // CertificateSerialNumber
            Object certSerial = certificateSerialNumberClass.getDeclaredConstructor(int.class)
                    .newInstance(secureRandom.nextInt() & 0x7fffffff);

            // AlgorithmId.get(algorithm)
            Method algorithmIdGet = algorithmIdClass.getMethod("get", String.class);
            Object algId = algorithmIdGet.invoke(null, CERTIFICATE_SIGNATURE_ALGORITHM);
            Object certAlgId = certificateAlgorithmIdClass.getDeclaredConstructor(algorithmIdClass).newInstance(algId);

            // X500Name
            Object x500Name = x500NameClass.getDeclaredConstructor(String.class).newInstance(cnString);

            // CertificateX509Key
            Object certKey = certificateX509KeyClass.getDeclaredConstructor(PublicKey.class).newInstance(publicKey);

            // CertificateValidity
            Object certValidity = certificateValidityClass.getDeclaredConstructor(Date.class, Date.class)
                    .newInstance(startDate, endDate);

            // Set fields on X509CertInfo - try setXXX methods first (newer JDK), fall back to set(String, Object)
            setInfoField(info, x509CertInfoClass, "version", "setVersion", certVersion, certificateVersionClass);
            setInfoField(info, x509CertInfoClass, "serialNumber", "setSerialNumber", certSerial, certificateSerialNumberClass);
            setInfoField(info, x509CertInfoClass, "algorithmId", "setAlgorithmId", certAlgId, certificateAlgorithmIdClass);
            setInfoField(info, x509CertInfoClass, "key", "setKey", certKey, certificateX509KeyClass);
            setInfoField(info, x509CertInfoClass, "validity", "setValidity", certValidity, certificateValidityClass);

            // Subject and Issuer need special handling due to API differences between JDK versions
            setSubjectOrIssuer(info, x509CertInfoClass, "subject", "setSubject", x500Name, x500NameClass);
            setSubjectOrIssuer(info, x509CertInfoClass, "issuer", "setIssuer", x500Name, x500NameClass);

            // Sign the certificate
            return signCertificate(info, x509CertInfoClass, x509CertImplClass, privateKey);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                 IllegalAccessException | InvocationTargetException e) {
            throw new GeneralSecurityException("Failed to generate self-signed certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Set a field on X509CertInfo. Try the newer setXXX method first,
     * fall back to the set(String, Object) method for older JDKs.
     */
    private static void setInfoField(Object info, Class<?> infoClass, String fieldName,
                                     String setterName, Object value, Class<?> valueClass)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        try {
            // Try newer JDK setter method (e.g., setVersion, setSerialNumber)
            Method setter = infoClass.getMethod(setterName, valueClass);
            setter.invoke(info, value);
        } catch (NoSuchMethodException e) {
            // Fall back to set(String, Object) for Java 8
            Method setMethod = infoClass.getMethod("set", String.class, Object.class);
            setMethod.invoke(info, fieldName, value);
        }
    }

    /**
     * Set subject or issuer on X509CertInfo. Handles API differences:
     * - Newer JDK: setSubject(X500Name) / setIssuer(X500Name)
     * - Java 8: set("subject", CertificateSubjectName) / set("issuer", CertificateIssuerName)
     */
    private static void setSubjectOrIssuer(Object info, Class<?> infoClass, String fieldName,
                                           String setterName, Object x500Name, Class<?> x500NameClass)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
            ClassNotFoundException, InstantiationException {
        try {
            // Try newer JDK setter (takes X500Name directly)
            Method setter = infoClass.getMethod(setterName, x500NameClass);
            setter.invoke(info, x500Name);
        } catch (NoSuchMethodException e) {
            // Fall back: in Java 8, subject/issuer need wrapper classes
            try {
                String wrapperClassName;
                if ("subject".equals(fieldName)) {
                    wrapperClassName = "sun.security.x509.CertificateSubjectName";
                } else {
                    wrapperClassName = "sun.security.x509.CertificateIssuerName";
                }
                Class<?> wrapperClass = Class.forName(wrapperClassName);
                Object wrapper = wrapperClass.getDeclaredConstructor(x500NameClass).newInstance(x500Name);
                Method setMethod = infoClass.getMethod("set", String.class, Object.class);
                setMethod.invoke(info, fieldName, wrapper);
            } catch (ClassNotFoundException cnfe) {
                // Wrapper classes don't exist, try set with X500Name directly
                Method setMethod = infoClass.getMethod("set", String.class, Object.class);
                setMethod.invoke(info, fieldName, x500Name);
            }
        }
    }

    /**
     * Sign the certificate. Try newer static factory method first,
     * fall back to instance sign method for Java 8.
     */
    private static X509Certificate signCertificate(Object info, Class<?> infoClass,
                                                   Class<?> certImplClass, PrivateKey privateKey)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        try {
            // Try newer JDK: X509CertImpl.newSigned(X509CertInfo, PrivateKey, String)
            Method newSigned = certImplClass.getMethod("newSigned", infoClass, PrivateKey.class, String.class);
            return (X509Certificate) newSigned.invoke(null, info, privateKey, CERTIFICATE_SIGNATURE_ALGORITHM);
        } catch (NoSuchMethodException e) {
            // Fall back to Java 8: new X509CertImpl(X509CertInfo), then cert.sign(PrivateKey, String)
            Constructor<?> constructor = certImplClass.getDeclaredConstructor(infoClass);
            Object cert = constructor.newInstance(info);
            Method signMethod = certImplClass.getMethod("sign", PrivateKey.class, String.class);
            signMethod.invoke(cert, privateKey, CERTIFICATE_SIGNATURE_ALGORITHM);
            return (X509Certificate) cert;
        }
    }
}
