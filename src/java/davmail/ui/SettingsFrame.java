package davmail.ui;

import davmail.DavGateway;
import davmail.Settings;
import davmail.tray.DavGatewayTray;
import org.apache.log4j.Level;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * DavMail settings frame
 */
public class SettingsFrame extends JFrame {
    public static final Level[] LOG_LEVELS = {Level.OFF, Level.FATAL, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.ALL};

    protected JTextField urlField;
    protected JTextField popPortField;
    protected JTextField smtpPortField;
    protected JTextField keepDelayField;

    JCheckBox enableProxyField;
    JTextField httpProxyField;
    JTextField httpProxyPortField;
    JTextField httpProxyUserField;
    JTextField httpProxyPasswordField;

    JCheckBox allowRemoteField;
    JTextField bindAddressField;
    JTextField certHashField;

    JComboBox rootLoggingLevelField;
    JComboBox davmailLoggingLevelField;
    JComboBox httpclientLoggingLevelField;
    JComboBox wireLoggingLevelField;

    protected void addSettingComponent(JPanel panel, String label, Component component) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(fieldLabel);
        panel.add(component);
    }

    protected JPanel getSettingsPanel() {
        JPanel settingsPanel = new JPanel(new GridLayout(4, 2));
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Gateway"));

        urlField = new JTextField(Settings.getProperty("davmail.url"), 15);
        urlField.setToolTipText("Base outlook web access URL");
        popPortField = new JTextField(Settings.getProperty("davmail.popPort"), 4);
        smtpPortField = new JTextField(Settings.getProperty("davmail.smtpPort"), 4);
        keepDelayField = new JTextField(Settings.getProperty("davmail.keepDelay"), 4);
        keepDelayField.setToolTipText("Number of days to keep messages in trash");


        addSettingComponent(settingsPanel, "OWA url: ", urlField);
        addSettingComponent(settingsPanel, "Local POP port: ", popPortField);
        addSettingComponent(settingsPanel, "Local SMTP port: ", smtpPortField);
        addSettingComponent(settingsPanel, "Keep Delay: ", keepDelayField);
        return settingsPanel;
    }

    protected JPanel getProxyPanel() {
        JPanel proxyPanel = new JPanel(new GridLayout(5, 2));
        proxyPanel.setBorder(BorderFactory.createTitledBorder("Proxy"));

        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        enableProxyField = new JCheckBox();
        enableProxyField.setSelected(enableProxy);
        httpProxyField = new JTextField(Settings.getProperty("davmail.proxyHost"), 15);
        httpProxyPortField = new JTextField(Settings.getProperty("davmail.proxyPort"), 4);
        httpProxyUserField = new JTextField(Settings.getProperty("davmail.proxyUser"), 4);
        httpProxyPasswordField = new JPasswordField(Settings.getProperty("davmail.proxyPassword"), 4);

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

        addSettingComponent(proxyPanel, "Enable proxy: ", enableProxyField);
        addSettingComponent(proxyPanel, "Proxy server: ", httpProxyField);
        addSettingComponent(proxyPanel, "Proxy port: ", httpProxyPortField);
        addSettingComponent(proxyPanel, "Proxy user: ", httpProxyUserField);
        addSettingComponent(proxyPanel, "Proxy password: ", httpProxyPasswordField);
        return proxyPanel;
    }

    public JPanel getNetworkSettingsPanel() {
        JPanel networkSettingsPanel = new JPanel(new GridLayout(3, 2));
        networkSettingsPanel.setBorder(BorderFactory.createTitledBorder("Network"));

        allowRemoteField = new JCheckBox();
        allowRemoteField.setSelected(Settings.getBooleanProperty("davmail.allowRemote"));
        allowRemoteField.setToolTipText("Allow remote connections to the gateway (server mode)");

        bindAddressField = new JTextField(Settings.getProperty("davmail.bindAddress"), 15);
        bindAddressField.setToolTipText("Bind only to the specified network address");

        certHashField = new JTextField(Settings.getProperty("davmail.server.certificate.hash"), 15);
        certHashField.setToolTipText("Manually accepted server certificate hash");

        addSettingComponent(networkSettingsPanel, "Bind address: ", bindAddressField);
        addSettingComponent(networkSettingsPanel, "Allow Remote Connections: ", allowRemoteField);
        addSettingComponent(networkSettingsPanel, "Server certificate hash: ", certHashField);
        return networkSettingsPanel;
    }

    public JPanel getLoggingSettingsPanel() {
        JPanel loggingSettingsPanel = new JPanel(new GridLayout(4, 2));
        loggingSettingsPanel.setBorder(BorderFactory.createTitledBorder("Logging levels"));

        rootLoggingLevelField = new JComboBox(LOG_LEVELS);
        davmailLoggingLevelField = new JComboBox(LOG_LEVELS);
        httpclientLoggingLevelField = new JComboBox(LOG_LEVELS);
        wireLoggingLevelField = new JComboBox(LOG_LEVELS);

        addSettingComponent(loggingSettingsPanel, "Default: ", rootLoggingLevelField);
        addSettingComponent(loggingSettingsPanel, "DavMail: ", davmailLoggingLevelField);
        addSettingComponent(loggingSettingsPanel, "HttpClient: ", httpclientLoggingLevelField);
        addSettingComponent(loggingSettingsPanel, "Wire: ", wireLoggingLevelField);

        return loggingSettingsPanel;
    }

    public void reload() {
        // reload settings in form
        urlField.setText(Settings.getProperty("davmail.url"));
        popPortField.setText(Settings.getProperty("davmail.popPort"));
        smtpPortField.setText(Settings.getProperty("davmail.smtpPort"));
        keepDelayField.setText(Settings.getProperty("davmail.keepDelay"));
        allowRemoteField.setSelected(Settings.getBooleanProperty(("davmail.allowRemote")));
        bindAddressField.setText(Settings.getProperty("davmail.bindAddress"));
        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        enableProxyField.setSelected(enableProxy);
        httpProxyField.setEnabled(enableProxy);
        httpProxyPortField.setEnabled(enableProxy);
        httpProxyUserField.setEnabled(enableProxy);
        httpProxyPasswordField.setEnabled(enableProxy);
        httpProxyField.setText(Settings.getProperty("davmail.proxyHost"));
        httpProxyPortField.setText(Settings.getProperty("davmail.proxyPort"));
        httpProxyUserField.setText(Settings.getProperty("davmail.proxyUser"));
        httpProxyPasswordField.setText(Settings.getProperty("davmail.proxyPassword"));
        certHashField.setText(Settings.getProperty("davmail.server.certificate.hash"));

        rootLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("rootLogger"));
        davmailLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("davmail"));
        httpclientLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("org.apache.commons.httpclient"));
        wireLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("httpclient.wire"));
    }

    public SettingsFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setTitle("DavMail Gateway Settings");
        setIconImage(DavGatewayTray.getFrameIcon());

        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(getSettingsPanel());
        mainPanel.add(getProxyPanel());
        mainPanel.add(Box.createVerticalGlue());

        tabbedPane.add("Main", mainPanel);

        JPanel advancedPanel = new JPanel();
        advancedPanel.setLayout(new BoxLayout(advancedPanel, BoxLayout.Y_AXIS));

        advancedPanel.add(getNetworkSettingsPanel());
        advancedPanel.add(getLoggingSettingsPanel());
        advancedPanel.add(Box.createVerticalGlue());

        tabbedPane.add("Advanced", advancedPanel);

        add("Center", tabbedPane);

        JPanel buttonPanel = new JPanel();
        JButton cancel = new JButton("Cancel");
        JButton ok = new JButton("Save");
        ActionListener save = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                // save options
                Settings.setProperty("davmail.url", urlField.getText());
                Settings.setProperty("davmail.popPort", popPortField.getText());
                Settings.setProperty("davmail.smtpPort", smtpPortField.getText());
                Settings.setProperty("davmail.keepDelay", keepDelayField.getText());
                Settings.setProperty("davmail.allowRemote", String.valueOf(allowRemoteField.isSelected()));
                Settings.setProperty("davmail.bindAddress", bindAddressField.getText());
                Settings.setProperty("davmail.enableProxy", String.valueOf(enableProxyField.isSelected()));
                Settings.setProperty("davmail.proxyHost", httpProxyField.getText());
                Settings.setProperty("davmail.proxyPort", httpProxyPortField.getText());
                Settings.setProperty("davmail.proxyUser", httpProxyUserField.getText());
                Settings.setProperty("davmail.proxyPassword", httpProxyPasswordField.getText());
                Settings.setProperty("davmail.server.certificate.hash", certHashField.getText());

                Settings.setLoggingLevel("rootLogger", (Level) rootLoggingLevelField.getSelectedItem());
                Settings.setLoggingLevel("davmail", (Level) davmailLoggingLevelField.getSelectedItem());
                Settings.setLoggingLevel("org.apache.commons.httpclient", (Level) httpclientLoggingLevelField.getSelectedItem());
                Settings.setLoggingLevel("httpclient.wire", (Level) wireLoggingLevelField.getSelectedItem());

                dispose();
                Settings.save();
                // restart listeners with new config
                DavGateway.start();
            }
        };
        ok.addActionListener(save);

        cancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                reload();
                dispose();
            }
        });

        buttonPanel.add(ok);
        buttonPanel.add(cancel);

        add("South", buttonPanel);

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
