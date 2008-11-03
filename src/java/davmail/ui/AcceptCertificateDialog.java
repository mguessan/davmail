package davmail.ui;

import davmail.http.DavGatewayX509TrustManager;
import davmail.tray.DavGatewayTray;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;

/**
 * Accept certificate dialog
 */
public class AcceptCertificateDialog extends JDialog {
    protected boolean accepted;

    public boolean isAccepted() {
        return accepted;
    }

    public void addFieldValue(JPanel panel, String label, String value) {
        JPanel fieldPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        StringBuilder buffer = new StringBuilder();
        buffer.append("<html><b>");
        buffer.append(label);
        buffer.append(":</b></html>");
        fieldPanel.add(new JLabel(buffer.toString()));
        fieldPanel.add(new JLabel(value));
        panel.add(fieldPanel);
    }

    protected String getRDN(Principal principal) {
        String dn = principal.getName();
        int start = dn.indexOf('=');
        int end = dn.indexOf(',');
        if (start >= 0 && end >= 0) {
            return dn.substring(start + 1, end);
        } else {
            return dn;
        }
    }

    public String getFormattedSerial(X509Certificate certificate) {
        StringBuilder builder = new StringBuilder();
        String serial = certificate.getSerialNumber().toString(16);
        for (int i = 0; i < serial.length(); i++) {
            if (i > 0 && i % 2 == 0) {
                builder.append(' ');
            }
            builder.append(serial.charAt(i));
        }
        return builder.toString();
    }

    public AcceptCertificateDialog(X509Certificate certificate) {
        setAlwaysOnTop(true);
        String sha1Hash;
        try {
            sha1Hash = DavGatewayX509TrustManager.getFormattedHash(certificate);
        } catch (NoSuchAlgorithmException nsa) {
            sha1Hash = nsa.getMessage();
        } catch (CertificateEncodingException cee) {
            sha1Hash = cee.getMessage();
        }
        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");

        setTitle("DavMail: Accept certificate ?");
        try {
            setIconImage(DavGatewayTray.getFrameIcon());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug("Unable to set JDialog icon image (not available under Java 1.5)");
        }

        JPanel subjectPanel = new JPanel();
        subjectPanel.setLayout(new BoxLayout(subjectPanel, BoxLayout.Y_AXIS));
        subjectPanel.setBorder(BorderFactory.createTitledBorder("Server Certificate"));
        addFieldValue(subjectPanel, "Issued to", getRDN(certificate.getSubjectDN()));
        addFieldValue(subjectPanel, "Issued by", getRDN(certificate.getIssuerDN()));
        addFieldValue(subjectPanel, "Valid from", formatter.format(certificate.getNotBefore()));
        addFieldValue(subjectPanel, "Valid until", formatter.format(certificate.getNotAfter()));
        addFieldValue(subjectPanel, "Serial", getFormattedSerial(certificate));
        addFieldValue(subjectPanel, "FingerPrint", sha1Hash);

        JPanel warningPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel imageLabel = new JLabel();
        imageLabel.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
        imageLabel.setText("<html><b>Server provided an untrusted certificate,<br> you can choose to accept or deny access</b></html>");
        warningPanel.add(imageLabel);
        add(warningPanel, BorderLayout.NORTH);
        add(subjectPanel, BorderLayout.CENTER);
        add(getButtonPanel(), BorderLayout.SOUTH);
        setModal(true);

        pack();
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        setVisible(true);
    }

    protected JPanel getButtonPanel() {
        JPanel buttonPanel = new JPanel();
        JButton accept = new JButton("Accept");
        JButton deny = new JButton("Deny");
        accept.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                accepted = true;
                dispose();
            }
        });
        deny.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                accepted = false;
                dispose();
            }
        });

        buttonPanel.add(accept);
        buttonPanel.add(deny);
        return buttonPanel;
    }


    public static boolean isCertificateTrusted(final X509Certificate certificate) {
        final boolean[] answer = new boolean[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    AcceptCertificateDialog certificateDialog = new AcceptCertificateDialog(certificate);
                    answer[0] = certificateDialog.isAccepted();
                }
            });
        } catch (InterruptedException ie) {
            DavGatewayTray.error("Error waiting for certificate check", ie);
        } catch (InvocationTargetException ite) {
            DavGatewayTray.error("Error waiting for certificate check", ite);
        }

        return answer[0];
    }
}
