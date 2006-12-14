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

        JPanel panel = new JPanel(new GridLayout(3, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Gateway settings"));

        final JTextField urlField = new JTextField("", 15);
        final JTextField popPortField = new JTextField(4);
        final JTextField smtpPortField = new JTextField(4);

        addSettingComponent(panel, "OWA url: ", urlField);
        addSettingComponent(panel, "Local POP port: ", popPortField);
        addSettingComponent(panel, "Local SMTP port: ", smtpPortField);

        add("North", panel);

        panel = new JPanel(new GridLayout(5, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Proxy settings"));

        final JCheckBox enableProxyField = new JCheckBox();
        final JTextField httpProxyField = new JTextField(System.getProperty("http.proxyHost"), 15);
        final JTextField httpProxyPortField = new JTextField(System.getProperty("http.proxyPort"), 4);
        final JTextField httpProxyUserField = new JTextField(System.getProperty("http.proxyUser"), 4);
        final JTextField httpProxyPasswordField = new JPasswordField (System.getProperty("http.proxyPassword"), 4);

        boolean enableProxy = enableProxyField.isSelected();
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
                // TODO : save options
                setVisible(false);
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
