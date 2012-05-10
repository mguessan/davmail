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
package davmail.ui;

import davmail.BundleMessage;
import davmail.ui.browser.DesktopBrowser;
import davmail.ui.tray.DavGatewayTray;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Edit Caldav scheduling notifications.
 */
public class NotificationDialog extends JDialog {

    protected boolean sendNotification;

    protected JTextField toField;
    protected JTextField ccField;
    protected JTextField subjectField;
    protected JEditorPane bodyField;

    protected void addRecipientComponent(JPanel panel, String label, JTextField textField, String toolTipText) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        fieldLabel.setVerticalAlignment(SwingConstants.CENTER);
        textField.setMaximumSize(textField.getPreferredSize());
        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
        innerPanel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        innerPanel.add(fieldLabel);
        innerPanel.add(textField);
        panel.add(innerPanel);
        if (toolTipText != null) {
            fieldLabel.setToolTipText(toolTipText);
            textField.setToolTipText(toolTipText);
        }
    }

    /**
     * Notification dialog to let user edit message body or cancel notification.
     *
     * @param to      main recipients
     * @param cc      copy recipients
     * @param subject notification subject
     */
    public NotificationDialog(String to, String cc, String subject, String description) {
        setModal(true);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setTitle(BundleMessage.format("UI_CALDAV_NOTIFICATION"));
        try {
            setIconImage(DavGatewayTray.getFrameIcon());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_SET_ICON_IMAGE"));
        }

        JPanel mainPanel = new JPanel();
        // add help (F1 handler)
        mainPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F1"),
                "help");
        mainPanel.getActionMap().put("help", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                DesktopBrowser.browse("http://davmail.sourceforge.net");
            }
        });

        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(getRecipientsPanel());
        mainPanel.add(getBodyPanel(description));

        JPanel recipientsPanel = getRecipientsPanel();
        if (to != null) {
            toField.setText(to);
        }
        if (cc != null) {
            ccField.setText(cc);
        }
        if (subject != null) {
            subjectField.setText(subject);
        }
        add(BorderLayout.NORTH, recipientsPanel);
        JPanel bodyPanel = getBodyPanel(description);
        add(BorderLayout.CENTER, bodyPanel);
        bodyField.setPreferredSize(recipientsPanel.getPreferredSize());

        JPanel buttonPanel = new JPanel();
        JButton cancel = new JButton(BundleMessage.format("UI_BUTTON_CANCEL"));
        JButton send = new JButton(BundleMessage.format("UI_BUTTON_SEND"));

        send.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                sendNotification = true;
                setVisible(false);
            }
        });

        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                // nothing to do, just hide
                setVisible(false);
            }
        });

        buttonPanel.add(send);
        buttonPanel.add(cancel);

        add(BorderLayout.SOUTH, buttonPanel);

        pack();
        setResizable(true);
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        bodyField.requestFocus();

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setVisible(true);
                toFront();
                repaint();
                requestFocus();
            }
        });

    }

    protected JPanel getRecipientsPanel() {
        JPanel recipientsPanel = new JPanel();
        recipientsPanel.setLayout(new BoxLayout(recipientsPanel, BoxLayout.Y_AXIS));
        recipientsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        toField = new JTextField("", 40);
        ccField = new JTextField("", 40);
        subjectField = new JTextField("", 40);

        addRecipientComponent(recipientsPanel, BundleMessage.format("UI_TO"), toField,
                BundleMessage.format("UI_TO_HELP"));
        addRecipientComponent(recipientsPanel, BundleMessage.format("UI_CC"), ccField,
                BundleMessage.format("UI_CC"));
        addRecipientComponent(recipientsPanel, BundleMessage.format("UI_SUBJECT"), subjectField,
                BundleMessage.format("UI_SUBJECT_HELP"));
        return recipientsPanel;
    }

    protected JPanel getBodyPanel(String description) {
        JPanel bodyPanel = new JPanel();
        bodyPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_NOTIFICATION_BODY")));

        bodyField = new JEditorPane();
        bodyField.setText(description);
        //HTMLEditorKit htmlEditorKit = new HTMLEditorKit();
        //bodyField.setEditorKit(htmlEditorKit);
        //bodyField.setContentType("text/html");

        bodyPanel.add(new JScrollPane(bodyField));
        return bodyPanel;
    }

    /**
     * Cancel notification flag.
     *
     * @return false if user chose to cancel notification
     */
    public boolean getSendNotification() {
        return sendNotification;
    }

    /**
     * Get edited recipients.
     *
     * @return recipients string
     */
    public String getTo() {
        return toField.getText();
    }

    /**
     * Get edited copy recipients.
     *
     * @return copy recipients string
     */
    public String getCc() {
        return ccField.getText();
    }

    /**
     * Get edited subject.
     *
     * @return subject
     */
    public String getSubject() {
        return subjectField.getText();
    }

    /**
     * Get edited body.
     *
     * @return edited notification body
     */
    public String getBody() {
        return bodyField.getText();
    }
}
