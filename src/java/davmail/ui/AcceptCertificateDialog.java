/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
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
package davmail.ui;

import davmail.BundleMessage;
import davmail.http.DavGatewayX509TrustManager;
import davmail.ui.tray.DavGatewayTray;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Date;

/**
 * Accept certificate dialog
 */
public class AcceptCertificateDialog extends JDialog {
    protected boolean accepted;

    /**
     * Accept status.
     *
     * @return true if user accepted certificate
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Add a new JLabel to panel with <b>label</b>: value text.
     *
     * @param panel certificate details panel
     * @param label certificate attribute label
     * @param value certificate attribute value
     */
    protected void addFieldValue(JPanel panel, String label, String value) {
        JPanel fieldPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        StringBuilder buffer = new StringBuilder();
        buffer.append("<html><b>");
        buffer.append(label);
        buffer.append(":</b></html>");
        fieldPanel.add(new JLabel(buffer.toString()));
        fieldPanel.add(new JLabel(value));
        panel.add(fieldPanel);
    }

    /**
     * Accept certificate dialog.
     *
     * @param certificate certificate sent by server
     */
    public AcceptCertificateDialog(X509Certificate certificate) {
        setAlwaysOnTop(true);
        String sha1Hash = DavGatewayX509TrustManager.getFormattedHash(certificate);
        DateFormat formatter = DateFormat.getDateInstance(DateFormat.MEDIUM);

        setTitle(BundleMessage.format("UI_ACCEPT_CERTIFICATE"));
        try {
            setIconImage(DavGatewayTray.getFrameIcon());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_SET_ICON_IMAGE"));
        }

        JPanel subjectPanel = new JPanel();
        subjectPanel.setLayout(new BoxLayout(subjectPanel, BoxLayout.Y_AXIS));
        subjectPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_SERVER_CERTIFICATE")));
        addFieldValue(subjectPanel, BundleMessage.format("UI_ISSUED_TO"), DavGatewayX509TrustManager.getRDN(certificate.getSubjectDN()));
        addFieldValue(subjectPanel, BundleMessage.format("UI_ISSUED_BY"), DavGatewayX509TrustManager.getRDN(certificate.getIssuerDN()));
        Date now = new Date();
        String notBefore = formatter.format(certificate.getNotBefore());
        if (now.before(certificate.getNotBefore())) {
            notBefore = "<html><font color=\"#FF0000\">" + notBefore + "</font></html>";
        }
        addFieldValue(subjectPanel, BundleMessage.format("UI_VALID_FROM"), notBefore);
        String notAfter = formatter.format(certificate.getNotAfter());
        if (now.after(certificate.getNotAfter())) {
            notAfter = "<html><font color=\"#FF0000\">" + notAfter + "</font></html>";
        }
        addFieldValue(subjectPanel, BundleMessage.format("UI_VALID_UNTIL"), notAfter);
        addFieldValue(subjectPanel, BundleMessage.format("UI_SERIAL"), DavGatewayX509TrustManager.getFormattedSerial(certificate));
        addFieldValue(subjectPanel, BundleMessage.format("UI_FINGERPRINT"), sha1Hash);

        JPanel warningPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel imageLabel = new JLabel();
        imageLabel.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
        imageLabel.setText(BundleMessage.format("UI_UNTRUSTED_CERTIFICATE_HTML"));
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
        toFront();
        requestFocus();
    }

    protected JPanel getButtonPanel() {
        JPanel buttonPanel = new JPanel();
        JButton accept = new JButton(BundleMessage.format("UI_BUTTON_ACCEPT"));
        JButton deny = new JButton(BundleMessage.format("UI_BUTTON_DENY"));
        accept.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                accepted = true;
                setVisible(false);
            }
        });
        deny.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                accepted = false;
                setVisible(false);
            }
        });

        buttonPanel.add(accept);
        buttonPanel.add(deny);
        return buttonPanel;
    }


    /**
     * Display certificate accept dialog and get user answer.
     *
     * @param certificate certificate sent by server
     * @return true if user accepted certificate
     */
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
            DavGatewayTray.error(new BundleMessage("UI_ERROR_WAITING_FOR_CERTIFICATE_CHECK"), ie);
        } catch (InvocationTargetException ite) {
            DavGatewayTray.error(new BundleMessage("UI_ERROR_WAITING_FOR_CERTIFICATE_CHECK"), ite);
        }

        return answer[0];
    }
}
