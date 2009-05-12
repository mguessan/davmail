package davmail.ui;

import davmail.BundleMessage;
import davmail.DavGateway;
import davmail.Settings;
import davmail.ui.tray.DavGatewayTray;
import org.apache.log4j.Level;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * DavMail settings frame
 */
public class SettingsFrame extends JFrame {
    static final Level[] LOG_LEVELS = {Level.OFF, Level.FATAL, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.ALL};

    protected JTextField urlField;
    protected JTextField popPortField;
    protected JCheckBox popPortCheckBox;
    protected JTextField imapPortField;
    protected JCheckBox imapPortCheckBox;
    protected JTextField smtpPortField;
    protected JCheckBox smtpPortCheckBox;
    protected JTextField caldavPortField;
    protected JCheckBox caldavPortCheckBox;
    protected JTextField ldapPortField;
    protected JCheckBox ldapPortCheckBox;
    protected JTextField keepDelayField;
    protected JTextField sentKeepDelayField;
    protected JTextField caldavPastDelayField;

    JCheckBox enableProxyField;
    JTextField httpProxyField;
    JTextField httpProxyPortField;
    JTextField httpProxyUserField;
    JTextField httpProxyPasswordField;

    JCheckBox allowRemoteField;
    JTextField bindAddressField;
    JTextField certHashField;
    JCheckBox disableUpdateCheck;

    JComboBox keystoreTypeCombo;
    JTextField keystoreFileField;
    JPasswordField keystorePassField;
    JPasswordField keyPassField;

    JComboBox rootLoggingLevelField;
    JComboBox davmailLoggingLevelField;
    JComboBox httpclientLoggingLevelField;
    JComboBox wireLoggingLevelField;

    protected void addSettingComponent(JPanel panel, String label, JComponent component) {
        addSettingComponent(panel, label, component, null);
    }

    protected void addSettingComponent(JPanel panel, String label, JComponent component, String toolTipText) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        fieldLabel.setVerticalAlignment(SwingConstants.CENTER);
        panel.add(fieldLabel);
        component.setMaximumSize(component.getPreferredSize());
        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
        innerPanel.add(component);
        panel.add(innerPanel);
        if (toolTipText != null) {
            fieldLabel.setToolTipText(toolTipText);
            component.setToolTipText(toolTipText);
        }
    }

    protected void addPortSettingComponent(JPanel panel, String label, JComponent component, JComponent checkboxComponent, String toolTipText) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        fieldLabel.setVerticalAlignment(SwingConstants.CENTER);
        panel.add(fieldLabel);
        component.setMaximumSize(component.getPreferredSize());
        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
        innerPanel.add(checkboxComponent);
        innerPanel.add(component);
        panel.add(innerPanel);
        if (toolTipText != null) {
            fieldLabel.setToolTipText(toolTipText);
            component.setToolTipText(toolTipText);
        }
    }

    protected JPanel getSettingsPanel() {
        JPanel settingsPanel = new JPanel(new GridLayout(6, 2));
        settingsPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_GATEWAY")));

        urlField = new JTextField(Settings.getProperty("davmail.url"), 17);
        popPortField = new JTextField(Settings.getProperty("davmail.popPort"), 4);
        popPortCheckBox = new JCheckBox();
        popPortCheckBox.setSelected(Settings.getProperty("davmail.popPort") != null && Settings.getProperty("davmail.popPort").length() > 0);
        popPortField.setEnabled(popPortCheckBox.isSelected());
        popPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                popPortField.setEnabled(popPortCheckBox.isSelected());
            }
        });

        imapPortField = new JTextField(Settings.getProperty("davmail.imapPort"), 4);
        imapPortCheckBox = new JCheckBox();
        imapPortCheckBox.setSelected(Settings.getProperty("davmail.imapPort") != null && Settings.getProperty("davmail.imapPort").length() > 0);
        imapPortField.setEnabled(imapPortCheckBox.isSelected());
        imapPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                imapPortField.setEnabled(imapPortCheckBox.isSelected());
            }
        });

        smtpPortField = new JTextField(Settings.getProperty("davmail.smtpPort"), 4);
        smtpPortCheckBox = new JCheckBox();
        smtpPortCheckBox.setSelected(Settings.getProperty("davmail.smtpPort") != null && Settings.getProperty("davmail.smtpPort").length() > 0);
        smtpPortField.setEnabled(smtpPortCheckBox.isSelected());
        smtpPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                smtpPortField.setEnabled(smtpPortCheckBox.isSelected());
            }
        });

        caldavPortField = new JTextField(Settings.getProperty("davmail.caldavPort"), 4);
        caldavPortCheckBox = new JCheckBox();
        caldavPortCheckBox.setSelected(Settings.getProperty("davmail.caldavPort") != null && Settings.getProperty("davmail.caldavPort").length() > 0);
        caldavPortField.setEnabled(caldavPortCheckBox.isSelected());
        caldavPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                caldavPortField.setEnabled(caldavPortCheckBox.isSelected());
            }
        });

        ldapPortField = new JTextField(Settings.getProperty("davmail.ldapPort"), 4);
        ldapPortCheckBox = new JCheckBox();
        ldapPortCheckBox.setSelected(Settings.getProperty("davmail.ldapPort") != null && Settings.getProperty("davmail.ldapPort").length() > 0);
        ldapPortField.setEnabled(ldapPortCheckBox.isSelected());
        ldapPortCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                ldapPortField.setEnabled(ldapPortCheckBox.isSelected());
            }
        });

        addSettingComponent(settingsPanel, BundleMessage.format("UI_OWA_URL"), urlField, BundleMessage.format("UI_OWA_URL_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_POP_PORT"), popPortField, popPortCheckBox,
                BundleMessage.format("UI_POP_PORT_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_IMAP_PORT"), imapPortField, imapPortCheckBox,
                BundleMessage.format("UI_IMAP_PORT_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_SMTP_PORT"), smtpPortField, smtpPortCheckBox,
                BundleMessage.format("UI_SMTP_PORT_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_CALDAV_PORT"), caldavPortField, caldavPortCheckBox,
                BundleMessage.format("UI_CALDAV_PORT_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_LDAP_PORT"), ldapPortField, ldapPortCheckBox,
                BundleMessage.format("UI_LDAP_PORT_HELP"));
        return settingsPanel;
    }

    protected JPanel getDelaysPanel() {
        JPanel delaysPanel = new JPanel(new GridLayout(3, 2));
        delaysPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_DELAYS")));

        keepDelayField = new JTextField(Settings.getProperty("davmail.keepDelay"), 4);
        sentKeepDelayField = new JTextField(Settings.getProperty("davmail.sentKeepDelay"), 4);
        caldavPastDelayField = new JTextField(Settings.getProperty("davmail.caldavPastDelay"), 4);

        addSettingComponent(delaysPanel, BundleMessage.format("UI_KEEP_DELAY"), keepDelayField,
                BundleMessage.format("UI_KEEP_DELAY_HELP"));
        addSettingComponent(delaysPanel, BundleMessage.format("UI_SENT_KEEP_DELAY"), sentKeepDelayField,
                BundleMessage.format("UI_SENT_KEEP_DELAY_HELP"));
        addSettingComponent(delaysPanel, BundleMessage.format("UI_CALENDAR_PAST_EVENTS"), caldavPastDelayField,
                BundleMessage.format("UI_CALENDAR_PAST_EVENTS_HELP"));
        return delaysPanel;
    }

    protected JPanel getProxyPanel() {
        JPanel proxyPanel = new JPanel(new GridLayout(5, 2));
        proxyPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_PROXY")));

        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        enableProxyField = new JCheckBox();
        enableProxyField.setSelected(enableProxy);
        httpProxyField = new JTextField(Settings.getProperty("davmail.proxyHost"), 15);
        httpProxyPortField = new JTextField(Settings.getProperty("davmail.proxyPort"), 4);
        httpProxyUserField = new JTextField(Settings.getProperty("davmail.proxyUser"), 10);
        httpProxyPasswordField = new JPasswordField(Settings.getProperty("davmail.proxyPassword"), 10);

        httpProxyField.setEnabled(enableProxy);
        httpProxyPortField.setEnabled(enableProxy);
        httpProxyUserField.setEnabled(enableProxy);
        httpProxyPasswordField.setEnabled(enableProxy);

        enableProxyField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                boolean newEnableProxy = enableProxyField.isSelected();
                httpProxyField.setEnabled(newEnableProxy);
                httpProxyPortField.setEnabled(newEnableProxy);
                httpProxyUserField.setEnabled(newEnableProxy);
                httpProxyPasswordField.setEnabled(newEnableProxy);
            }
        });

        addSettingComponent(proxyPanel, BundleMessage.format("UI_ENABLE_PROXY"), enableProxyField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_PROXY_SERVER"), httpProxyField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_PROXY_PORT"), httpProxyPortField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_PROXY_USER"), httpProxyUserField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_PROXY_PASSWORD"), httpProxyPasswordField);
        return proxyPanel;
    }

    protected JPanel getEncryptionPanel() {
        JPanel encryptionPanel = new JPanel(new GridLayout(4, 2));
        encryptionPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_CERTIFICATE")));

        keystoreTypeCombo = new JComboBox(new String[]{"JKS", "PKCS12"});
        keystoreTypeCombo.setSelectedItem(Settings.getProperty("davmail.ssl.keystoreType"));
        keystoreFileField = new JTextField(Settings.getProperty("davmail.ssl.keystoreFile"), 15);
        keystorePassField = new JPasswordField(Settings.getProperty("davmail.ssl.keystorePass"), 15);
        keyPassField = new JPasswordField(Settings.getProperty("davmail.ssl.keyPass"), 15);

        addSettingComponent(encryptionPanel, BundleMessage.format("UI_KEY_STORE_TYPE"), keystoreTypeCombo,
                BundleMessage.format("UI_KEY_STORE_TYPE_HELP"));
        addSettingComponent(encryptionPanel, BundleMessage.format("UI_KEY_STORE"), keystoreFileField,
                BundleMessage.format("UI_KEY_STORE_HELP"));
        addSettingComponent(encryptionPanel, BundleMessage.format("UI_KEY_STORE_PASSWORD"), keystorePassField,
                BundleMessage.format("UI_KEY_STORE_PASSWORD_HELP"));
        addSettingComponent(encryptionPanel, BundleMessage.format("UI_KEY_PASSWORD"), keyPassField,
                BundleMessage.format("UI_KEY_PASSWORD_HELP"));

        return encryptionPanel;
    }

    public JPanel getNetworkSettingsPanel() {
        JPanel networkSettingsPanel = new JPanel(new GridLayout(4, 2));
        networkSettingsPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_NETWORK")));

        allowRemoteField = new JCheckBox();
        allowRemoteField.setSelected(Settings.getBooleanProperty("davmail.allowRemote"));

        bindAddressField = new JTextField(Settings.getProperty("davmail.bindAddress"), 15);

        certHashField = new JTextField(Settings.getProperty("davmail.server.certificate.hash"), 15);

        disableUpdateCheck = new JCheckBox();
        disableUpdateCheck.setSelected(Settings.getBooleanProperty("davmail.disableUpdateCheck"));

        addSettingComponent(networkSettingsPanel, BundleMessage.format("UI_BIND_ADDRESS"), bindAddressField,
                BundleMessage.format("UI_BIND_ADDRESS_HELP"));
        addSettingComponent(networkSettingsPanel, BundleMessage.format("UI_ALLOW_REMOTE_CONNECTION"), allowRemoteField,
                BundleMessage.format("UI_ALLOW_REMOTE_CONNECTION_HELP"));
        addSettingComponent(networkSettingsPanel, BundleMessage.format("UI_SERVER_CERTIFICATE_HASH"), certHashField,
                BundleMessage.format("UI_SERVER_CERTIFICATE_HASH_HELP"));
        addSettingComponent(networkSettingsPanel, BundleMessage.format("UI_DISABLE_UPDATE_CHECK"), disableUpdateCheck,
                BundleMessage.format("UI_DISABLE_UPDATE_CHECK_HELP"));
        return networkSettingsPanel;
    }

    public JPanel getLoggingSettingsPanel() {
        JPanel loggingSettingsPanel = new JPanel(new GridLayout(4, 2));
        loggingSettingsPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_LOGGING_LEVELS")));

        rootLoggingLevelField = new JComboBox(LOG_LEVELS);
        davmailLoggingLevelField = new JComboBox(LOG_LEVELS);
        httpclientLoggingLevelField = new JComboBox(LOG_LEVELS);
        wireLoggingLevelField = new JComboBox(LOG_LEVELS);

        rootLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("rootLogger"));
        davmailLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("davmail"));
        httpclientLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("org.apache.commons.httpclient"));
        wireLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("httpclient.wire"));

        addSettingComponent(loggingSettingsPanel, BundleMessage.format("UI_LOG_DEFAULT"), rootLoggingLevelField);
        addSettingComponent(loggingSettingsPanel, BundleMessage.format("UI_LOG_DAVMAIL"), davmailLoggingLevelField);
        addSettingComponent(loggingSettingsPanel, BundleMessage.format("UI_LOG_HTTPCLIENT"), httpclientLoggingLevelField);
        addSettingComponent(loggingSettingsPanel, BundleMessage.format("UI_LOG_WIRE"), wireLoggingLevelField);

        return loggingSettingsPanel;
    }

    public void reload() {
        // reload settings in form
        urlField.setText(Settings.getProperty("davmail.url"));
        popPortField.setText(Settings.getProperty("davmail.popPort"));
        popPortCheckBox.setSelected(Settings.getProperty("davmail.popPort") != null && Settings.getProperty("davmail.popPort").length() > 0);
        imapPortField.setText(Settings.getProperty("davmail.imapPort"));
        imapPortCheckBox.setSelected(Settings.getProperty("davmail.imapPort") != null && Settings.getProperty("davmail.imapPort").length() > 0);
        smtpPortField.setText(Settings.getProperty("davmail.smtpPort"));
        smtpPortCheckBox.setSelected(Settings.getProperty("davmail.smtpPort") != null && Settings.getProperty("davmail.smtpPort").length() > 0);
        caldavPortField.setText(Settings.getProperty("davmail.caldavPort"));
        caldavPortCheckBox.setSelected(Settings.getProperty("davmail.caldavPort") != null && Settings.getProperty("davmail.caldavPort").length() > 0);
        ldapPortField.setText(Settings.getProperty("davmail.ldapPort"));
        ldapPortCheckBox.setSelected(Settings.getProperty("davmail.ldapPort") != null && Settings.getProperty("davmail.ldapPort").length() > 0);
        keepDelayField.setText(Settings.getProperty("davmail.keepDelay"));
        sentKeepDelayField.setText(Settings.getProperty("davmail.sentKeepDelay"));
        caldavPastDelayField.setText(Settings.getProperty("davmail.caldavPastDelay"));
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

        bindAddressField.setText(Settings.getProperty("davmail.bindAddress"));
        allowRemoteField.setSelected(Settings.getBooleanProperty(("davmail.allowRemote")));
        certHashField.setText(Settings.getProperty("davmail.server.certificate.hash"));
        disableUpdateCheck.setSelected(Settings.getBooleanProperty(("davmail.disableUpdateCheck")));

        keystoreTypeCombo.setSelectedItem(Settings.getProperty("davmail.ssl.keystoreType"));
        keystoreFileField.setText(Settings.getProperty("davmail.ssl.keystoreFile"));
        keystorePassField.setText(Settings.getProperty("davmail.ssl.keystorePass"));
        keyPassField.setText(Settings.getProperty("davmail.ssl.keyPass"));

        rootLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("rootLogger"));
        davmailLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("davmail"));
        httpclientLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("org.apache.commons.httpclient"));
        wireLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("httpclient.wire"));
    }

    public SettingsFrame() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setTitle(BundleMessage.format("UI_DAVMAIL_SETTINGS"));
        setIconImage(DavGatewayTray.getFrameIcon());

        JTabbedPane tabbedPane = new JTabbedPane();
        // add help (F1 handler)
        tabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F1"),
                "help");
        tabbedPane.getActionMap().put("help", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                DesktopBrowser.browse("http://davmail.sourceforge.net");
            }
        });

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(getSettingsPanel());
        mainPanel.add(getDelaysPanel());
        mainPanel.add(Box.createVerticalGlue());

        tabbedPane.add(BundleMessage.format("UI_TAB_MAIN"), mainPanel);

        JPanel advancedPanel = new JPanel();
        advancedPanel.setLayout(new BoxLayout(advancedPanel, BoxLayout.Y_AXIS));

        JPanel proxyPanel = new JPanel();
        proxyPanel.setLayout(new BoxLayout(proxyPanel, BoxLayout.Y_AXIS));
        proxyPanel.add(getProxyPanel());
        // empty panel
        proxyPanel.add(new JPanel());
        tabbedPane.add(BundleMessage.format("UI_TAB_PROXY"), proxyPanel);

        JPanel encryptionPanel = new JPanel();
        encryptionPanel.setLayout(new BoxLayout(encryptionPanel, BoxLayout.Y_AXIS));
        encryptionPanel.add(getEncryptionPanel());
        // empty panel
        encryptionPanel.add(new JPanel());
        tabbedPane.add(BundleMessage.format("UI_TAB_ENCRYPTION"), encryptionPanel);

        advancedPanel.add(getNetworkSettingsPanel());
        advancedPanel.add(getLoggingSettingsPanel());

        tabbedPane.add(BundleMessage.format("UI_TAB_ADVANCED"), advancedPanel);

        add(BorderLayout.CENTER, tabbedPane);

        JPanel buttonPanel = new JPanel();
        JButton cancel = new JButton(BundleMessage.format("UI_BUTTON_CANCEL"));
        JButton ok = new JButton(BundleMessage.format("UI_BUTTON_SAVE"));
        JButton help = new JButton(BundleMessage.format("UI_BUTTON_HELP"));
        ActionListener save = new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                // save options
                Settings.setProperty("davmail.url", urlField.getText());
                Settings.setProperty("davmail.popPort", popPortCheckBox.isSelected() ? popPortField.getText() : "");
                Settings.setProperty("davmail.imapPort", imapPortCheckBox.isSelected() ? imapPortField.getText() : "");
                Settings.setProperty("davmail.smtpPort", smtpPortCheckBox.isSelected() ? smtpPortField.getText() : "");
                Settings.setProperty("davmail.caldavPort", caldavPortCheckBox.isSelected() ? caldavPortField.getText() : "");
                Settings.setProperty("davmail.ldapPort", ldapPortCheckBox.isSelected() ? ldapPortField.getText() : "");
                Settings.setProperty("davmail.keepDelay", keepDelayField.getText());
                Settings.setProperty("davmail.sentKeepDelay", sentKeepDelayField.getText());
                Settings.setProperty("davmail.caldavPastDelay", caldavPastDelayField.getText());
                Settings.setProperty("davmail.enableProxy", String.valueOf(enableProxyField.isSelected()));
                Settings.setProperty("davmail.proxyHost", httpProxyField.getText());
                Settings.setProperty("davmail.proxyPort", httpProxyPortField.getText());
                Settings.setProperty("davmail.proxyUser", httpProxyUserField.getText());
                Settings.setProperty("davmail.proxyPassword", httpProxyPasswordField.getText());

                Settings.setProperty("davmail.bindAddress", bindAddressField.getText());
                Settings.setProperty("davmail.allowRemote", String.valueOf(allowRemoteField.isSelected()));
                Settings.setProperty("davmail.server.certificate.hash", certHashField.getText());
                Settings.setProperty("davmail.disableUpdateCheck", String.valueOf(disableUpdateCheck.isSelected()));
                Settings.setProperty("davmail.ssl.keystoreType", (String) keystoreTypeCombo.getSelectedItem());
                Settings.setProperty("davmail.ssl.keystoreFile", keystoreFileField.getText());
                Settings.setProperty("davmail.ssl.keystorePass", String.valueOf(keystorePassField.getPassword()));
                Settings.setProperty("davmail.ssl.keyPass", String.valueOf(keyPassField.getPassword()));

                Settings.setLoggingLevel("rootLogger", (Level) rootLoggingLevelField.getSelectedItem());
                Settings.setLoggingLevel("davmail", (Level) davmailLoggingLevelField.getSelectedItem());
                Settings.setLoggingLevel("org.apache.commons.httpclient", (Level) httpclientLoggingLevelField.getSelectedItem());
                Settings.setLoggingLevel("httpclient.wire", (Level) wireLoggingLevelField.getSelectedItem());

                dispose();
                Settings.save();
                // restart listeners with new config
                DavGateway.stop();
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

        help.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                DesktopBrowser.browse("http://davmail.sourceforge.net");
            }
        });

        buttonPanel.add(ok);
        buttonPanel.add(cancel);
        buttonPanel.add(help);

        add(BorderLayout.SOUTH, buttonPanel);

        pack();
        //setResizable(false);
        // center frame
        setLocation(getToolkit().getScreenSize().width / 2 -
                getSize().width / 2,
                getToolkit().getScreenSize().height / 2 -
                        getSize().height / 2);
        urlField.requestFocus();
    }
}
