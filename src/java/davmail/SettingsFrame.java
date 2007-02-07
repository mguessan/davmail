package davmail;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;

/**
 * DavMail settings frame
 */
public class SettingsFrame extends JFrame {
    protected void addSettingComponent(JPanel panel, String label, Component component) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(fieldLabel);
        panel.add(component);
    }

    public SettingsFrame() {
        setTitle("DavMail Settings");

        JPanel panel = new JPanel(new GridLayout(4, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Gateway settings"));

        final JTextField urlField = new JTextField(Settings.getProperty("davmail.url"), 15);
        final JTextField popPortField = new JTextField(Settings.getProperty("davmail.popPort"), 4);
        final JTextField smtpPortField = new JTextField(Settings.getProperty("davmail.smtpPort"), 4);
        final JTextField keepDelayField = new JTextField(Settings.getProperty("davmail.keepDelay"), 4);
        keepDelayField.setToolTipText("Number of days to keep messages in trash");

        addSettingComponent(panel, "OWA url: ", urlField);
        addSettingComponent(panel, "Local POP port: ", popPortField);
        addSettingComponent(panel, "Local SMTP port: ", smtpPortField);
        addSettingComponent(panel, "Keep Delay: ", keepDelayField);

        add("North", panel);

        panel = new JPanel(new GridLayout(5, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Proxy settings"));

        boolean enableProxy = "true".equals(Settings.getProperty("davmail.enableProxy"));
        final JCheckBox enableProxyField = new JCheckBox();
        enableProxyField.setSelected(enableProxy);
        final JTextField httpProxyField = new JTextField(Settings.getProperty("davmail.proxyHost"), 15);
        final JTextField httpProxyPortField = new JTextField(Settings.getProperty("davmail.proxyPort"), 4);
        final JTextField httpProxyUserField = new JTextField(Settings.getProperty("davmail.proxyUser"), 4);
        final JTextField httpProxyPasswordField = new JPasswordField (Settings.getProperty("davmail.proxyPassword"), 4);

        httpProxyField.setEnabled(enableProxy);
        httpProxyPortField.setEnabled(enableProxy);
        httpProxyUserField.setEnabled(enableProxy);
        httpProxyPasswordField.setEnabled(enableProxy);

        enableProxyField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                boolean enableProxy = enableProxyField.isSelected();
                httpProxyField.setEnabled(enableProxy);
                httpProxyPortField.setEnabled(enableProxy);
                httpProxyUserField.setEnabled(enableProxy);
                httpProxyPasswordField.setEnabled(enableProxy);
            }
        });

        addSettingComponent(panel, "Enable proxy: ", enableProxyField);
        addSettingComponent(panel, "Proxy server: ", httpProxyField);
        addSettingComponent(panel, "Proxy port: ", httpProxyPortField);
        addSettingComponent(panel, "Proxy user: ", httpProxyUserField);
        addSettingComponent(panel, "Proxy password: ", httpProxyPasswordField);

        add("Center", panel);

        panel = new JPanel();
        JButton cancel = new JButton("Cancel");
        JButton ok = new JButton("Save");
        ActionListener save = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                // save options
                 Settings.setProperty("davmail.url", urlField.getText());
                Settings.setProperty("davmail.popPort", popPortField.getText());
                Settings.setProperty("davmail.smtpPort", smtpPortField.getText());
                Settings.setProperty("davmail.keepDelay", keepDelayField.getText());
                Settings.setProperty("davmail.enableProxy", String.valueOf(enableProxyField.isSelected()));
                Settings.setProperty("davmail.proxyHost", httpProxyField.getText());
                Settings.setProperty("davmail.proxyPort", httpProxyPortField.getText());
                Settings.setProperty("davmail.proxyUser", httpProxyUserField.getText());
                Settings.setProperty("davmail.proxyPassword", httpProxyPasswordField.getText());
                Settings.save();
                setVisible(false);
                // restart listeners with new config
                DavGateway.start();
            }
        };
        ok.addActionListener(save);

        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                setVisible(false);
            }
        });

        panel.add(ok);
        panel.add(cancel);

        add("South", panel);

        pack();
        setResizable(false);
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        urlField.requestFocus();
    }
}
