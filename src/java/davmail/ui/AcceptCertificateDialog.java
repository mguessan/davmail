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
import java.util.Date;

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

    public AcceptCertificateDialog(X509Certificate certificate) {
        setAlwaysOnTop(true);
        String sha1Hash = DavGatewayX509TrustManager.getFormattedHash(certificate);
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
        addFieldValue(subjectPanel, "Issued to", DavGatewayX509TrustManager.getRDN(certificate.getSubjectDN()));
        addFieldValue(subjectPanel, "Issued by", DavGatewayX509TrustManager.getRDN(certificate.getIssuerDN()));
        Date now = new Date();
        String notBefore = formatter.format(certificate.getNotBefore());
        if (now.before(certificate.getNotBefore())) {
           notBefore = "<html><font color=\"#FF0000\">"+notBefore+"</font></html>"; 
        }
        addFieldValue(subjectPanel, "Valid from", notBefore);
        String notAfter = formatter.format(certificate.getNotAfter());
        if (now.after(certificate.getNotAfter())) {
           notAfter = "<html><font color=\"#FF0000\">"+notAfter+"</font></html>";
        }
        addFieldValue(subjectPanel, "Valid until", notAfter);
        addFieldValue(subjectPanel, "Serial", DavGatewayX509TrustManager.getFormattedSerial(certificate));
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
