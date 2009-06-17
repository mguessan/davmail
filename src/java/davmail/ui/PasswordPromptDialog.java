package davmail.ui;

import davmail.BundleMessage;
import davmail.ui.tray.DavGatewayTray;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Get smartcard password
 */
public class PasswordPromptDialog extends JDialog {
    final JPasswordField passwordField = new JPasswordField(20);
    protected char[] password;

    public char[] getPassword() {
        return password;
    }

    public PasswordPromptDialog(String prompt) {
        setAlwaysOnTop(true);

        setTitle(BundleMessage.format("UI_PASSWORD_PROMPT"));
        try {
            setIconImage(DavGatewayTray.getFrameIcon());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_SET_ICON_IMAGE"));
        }


        JPanel questionPanel = new JPanel();
        questionPanel.setLayout(new BoxLayout(questionPanel, BoxLayout.Y_AXIS));
        JLabel imageLabel = new JLabel();
        imageLabel.setIcon(UIManager.getIcon("OptionPane.questionIcon"));
        imageLabel.setText(prompt);
        questionPanel.add(imageLabel);

        passwordField.setMaximumSize(passwordField.getPreferredSize());
        passwordField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                password = passwordField.getPassword();
                dispose();
            }
        });
        JPanel passwordPanel = new JPanel();
        passwordPanel.setLayout(new BoxLayout(passwordPanel, BoxLayout.Y_AXIS));
        passwordPanel.add(passwordField);
        //questionPanel.add(passwordField);
        add(questionPanel, BorderLayout.NORTH);
        add(passwordPanel, BorderLayout.CENTER);
        add(getButtonPanel(), BorderLayout.SOUTH);
        setModal(true);

        pack();
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        setVisible(true);
        requestFocus();
    }

    protected JPanel getButtonPanel() {
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton(BundleMessage.format("UI_BUTTON_OK"));
        JButton cancelButton = new JButton(BundleMessage.format("UI_BUTTON_CANCEL"));
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                password = passwordField.getPassword();
                dispose();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                password = null;
                dispose();
            }
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        return buttonPanel;
    }

}
