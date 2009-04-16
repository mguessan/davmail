package davmail.http;

import davmail.Settings;
import davmail.ui.tray.DavGatewayTray;
import davmail.ui.AcceptCertificateDialog;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.awt.*;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Custom Trust Manager, let user accept or deny.
 */
public class DavGatewayX509TrustManager implements X509TrustManager {
    private final X509TrustManager standardTrustManager;

    public DavGatewayX509TrustManager() throws NoSuchAlgorithmException, KeyStoreException {
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

    public X509Certificate[] getAcceptedIssuers() {
        return this.standardTrustManager.getAcceptedIssuers();
    }

    protected void userCheckServerTrusted(final X509Certificate[] x509Certificates) throws CertificateException {
        String acceptedCertificateHash = Settings.getProperty("davmail.server.certificate.hash");
        String certificateHash = getFormattedHash(x509Certificates[0]);
        // if user already accepted a certificate,
        if (acceptedCertificateHash != null && acceptedCertificateHash.length() > 0
                && acceptedCertificateHash.equals(certificateHash)) {
            DavGatewayTray.debug("Found permanently accepted certificate, hash " + acceptedCertificateHash);
        } else {
            boolean isCertificateTrusted;
            if (Settings.getBooleanProperty("davmail.server")) {
                // headless mode
                isCertificateTrusted = isCertificateTrusted(x509Certificates[0]);
            } else {
                isCertificateTrusted = AcceptCertificateDialog.isCertificateTrusted(x509Certificates[0]);
            }
            if (!isCertificateTrusted) {
                throw new CertificateException("User rejected certificate");
            }
            // certificate accepted, store in settings
            Settings.saveProperty("davmail.server.certificate.hash", certificateHash);
        }
    }

    @SuppressWarnings({"UseOfSystemOutOrSystemErr"})
    protected boolean isCertificateTrusted(X509Certificate certificate) {
        BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
        String answer = null;
        while (!"y".equals(answer) && !"Y".equals(answer) && !"n".equals(answer) && !"N".equals(answer) ) {
            System.out.println("Server Certificate:");
            System.out.println("Issued to: " + DavGatewayX509TrustManager.getRDN(certificate.getSubjectDN()));
            System.out.println("Issued by: " + getRDN(certificate.getIssuerDN()));
            SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
            String notBefore = formatter.format(certificate.getNotBefore());
            System.out.println("Valid from: " + notBefore);
            String notAfter = formatter.format(certificate.getNotAfter());
            System.out.println("Valid until: " + notAfter);
            System.out.println("Serial: " + getFormattedSerial(certificate));
            String sha1Hash = DavGatewayX509TrustManager.getFormattedHash(certificate);
            System.out.println("FingerPrint: " + sha1Hash);
            System.out.println();
            System.out.println("Server provided an untrusted certificate,");
            System.out.println("you can choose to accept or deny access.");
            System.out.println("Accept certificate (y/n)?");
            try {
                answer = inReader.readLine();
            } catch (IOException e) {
                System.err.println(e);
            }
        }
        return "y".equals(answer) || "Y".equals(answer);
    }

    public static String getRDN(Principal principal) {
        String dn = principal.getName();
        int start = dn.indexOf('=');
        int end = dn.indexOf(',');
        if (start >= 0 && end >= 0) {
            return dn.substring(start + 1, end);
        } else {
            return dn;
        }
    }

    public static String getFormattedSerial(X509Certificate certificate) {
        StringBuilder builder = new StringBuilder();
        String serial = certificate.getSerialNumber().toString(16);
        for (int i = 0; i < serial.length(); i++) {
            if (i > 0 && i % 2 == 0) {
                builder.append(' ');
            }
            builder.append(serial.charAt(i));
        }
        return builder.toString().toUpperCase();
    }

    public static String getFormattedHash(X509Certificate certificate) {
        String sha1Hash;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] digest = md.digest(certificate.getEncoded());
            sha1Hash = formatHash(digest);
        } catch (NoSuchAlgorithmException nsa) {
            sha1Hash = nsa.getMessage();
        } catch (CertificateEncodingException cee) {
            sha1Hash = cee.getMessage();
        }
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
