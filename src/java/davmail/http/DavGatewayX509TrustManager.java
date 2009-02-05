package davmail.http;

import davmail.Settings;
import davmail.tray.DavGatewayTray;
import davmail.ui.AcceptCertificateDialog;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.awt.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Custom Trust Manager, let user accept or deny.
 */
public class DavGatewayX509TrustManager implements X509TrustManager {
    private X509TrustManager standardTrustManager = null;

    public DavGatewayX509TrustManager() throws NoSuchAlgorithmException, KeyStoreException {
        super();
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        TrustManager[] trustmanagers = factory.getTrustManagers();
        if (trustmanagers.length == 0) {
            throw new NoSuchAlgorithmException("No trust manager found");
        }
        this.standardTrustManager = (X509TrustManager) trustmanagers[0];
    }

    public void checkServerTrusted(X509Certificate[] x509Certificates, String authType) throws CertificateException {
        try {
            // first try standard Trust Manager
            this.standardTrustManager.checkServerTrusted(x509Certificates, authType);
        } catch (CertificateException e) {
            if ((x509Certificates != null) && (x509Certificates.length > 0) && !GraphicsEnvironment.isHeadless()) {
                userCheckServerTrusted(x509Certificates);
            } else {
                throw e;
            }

        }
    }

    public void checkClientTrusted(X509Certificate[] x509Certificates, String authType) throws CertificateException {
        this.standardTrustManager.checkClientTrusted(x509Certificates, authType);
    }

    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return this.standardTrustManager.getAcceptedIssuers();
    }

    protected void userCheckServerTrusted(final X509Certificate[] x509Certificates) throws CertificateException {
        try {
            String acceptedCertificateHash = Settings.getProperty("davmail.server.certificate.hash");
            String certificateHash = getFormattedHash(x509Certificates[0]);
            // if user already accepted a certificate,
            if (acceptedCertificateHash != null && acceptedCertificateHash.length() > 0
                    && acceptedCertificateHash.equals(certificateHash)) {
                DavGatewayTray.debug("Found permanently accepted certificate, hash " + acceptedCertificateHash);
            } else {

                if (!AcceptCertificateDialog.isCertificateTrusted(x509Certificates[0])) {
                    throw new CertificateException("User rejected certificate");
                }
                // certificate accepted, store in settings
                Settings.saveProperty("davmail.server.certificate.hash", certificateHash);
            }
        } catch (NoSuchAlgorithmException nsa) {
            throw new CertificateException(nsa);
        }
    }

    public static String getFormattedHash(X509Certificate certificate) throws NoSuchAlgorithmException, CertificateEncodingException {
        String sha1Hash;
        MessageDigest md = MessageDigest.getInstance("SHA1");
        byte[] digest = md.digest(certificate.getEncoded());
        sha1Hash = formatHash(digest);
        return sha1Hash;
    }

    public static String formatHash(byte[] buffer) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < buffer.length; i++) {
            if (i > 0) {
                builder.append(':');
            }
            builder.append(Integer.toHexString(buffer[i] & 0xFF));
        }
        return builder.toString().toUpperCase();
    }
}
