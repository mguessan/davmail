package davmail.ui;

import davmail.BundleMessage;
import davmail.ui.tray.DavGatewayTray;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Prompt for Exchange credential and password.
 */
public class CredentialPromptDialog extends JDialog {
    final JTextField principalField = new JTextField(15);
    final JPasswordField passwordField = new JPasswordField(15);
    protected String principal;
    protected char[] password;

    /**
     * Get user password.
     *
     * @return user password as char array
     */
    public char[] getPassword() {
        if (password != null) {
            return password.clone();
        } else {
            return "".toCharArray();
        }
    }

    public String getPrincipal() {
        return principal;
    }

    /**
     * Get credentials.
     *
     * @param prompt Kerberos prompt from callback handler
     */
    public CredentialPromptDialog(String prompt) {
        setAlwaysOnTop(true);

        setTitle(BundleMessage.format("UI_KERBEROS_CREDENTIAL_PROMPT"));

        try {
            //noinspection Since15
            setIconImage(DavGatewayTray.getFrameIcon());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_SET_ICON_IMAGE"));
        }


        JPanel questionPanel = new JPanel();
        questionPanel.setLayout(new BoxLayout(questionPanel, BoxLayout.Y_AXIS));
        JLabel imageLabel = new JLabel();
        imageLabel.setIcon(UIManager.getIcon("OptionPane.questionIcon"));
        questionPanel.add(imageLabel);

        passwordField.setMaximumSize(passwordField.getPreferredSize());
        passwordField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                principal = principalField.getText();
                password = passwordField.getPassword();
                setVisible(false);
            }
        });
        JPanel credentialPanel = new JPanel(new GridLayout(2, 2));

        JLabel promptLabel = new JLabel(" "+prompt.trim());
        promptLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        promptLabel.setVerticalAlignment(SwingConstants.CENTER);

        credentialPanel.add(promptLabel);

        principalField.setMaximumSize(principalField.getPreferredSize());
        credentialPanel.add(principalField);

        JLabel passwordLabel = new JLabel(BundleMessage.format("UI_KERBEROS_PASSWORD_PROMPT"));
        passwordLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        passwordLabel.setVerticalAlignment(SwingConstants.CENTER);
        credentialPanel.add(passwordLabel);

        passwordField.setMaximumSize(passwordField.getPreferredSize());
        credentialPanel.add(passwordField);

        add(questionPanel, BorderLayout.WEST);
        add(credentialPanel, BorderLayout.CENTER);
        add(getButtonPanel(), BorderLayout.SOUTH);
        setModal(true);

        pack();
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        setAlwaysOnTop(true);
        setVisible(true);
    }

    protected JPanel getButtonPanel() {
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton(BundleMessage.format("UI_BUTTON_OK"));
        JButton cancelButton = new JButton(BundleMessage.format("UI_BUTTON_CANCEL"));
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                principal = principalField.getText();
                password = passwordField.getPassword();
                setVisible(false);
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                principal = null;
                password = null;
                setVisible(false);
            }
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }

}
