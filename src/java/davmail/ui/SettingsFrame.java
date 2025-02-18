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
import davmail.DavGateway;
import davmail.Settings;
import davmail.ui.browser.DesktopBrowser;
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
    protected JCheckBox popNoSSLCheckBox;
    protected JTextField imapPortField;
    protected JCheckBox imapPortCheckBox;
    protected JCheckBox imapNoSSLCheckBox;
    protected JTextField smtpPortField;
    protected JCheckBox smtpPortCheckBox;
    protected JCheckBox smtpNoSSLCheckBox;
    protected JTextField caldavPortField;
    protected JCheckBox caldavPortCheckBox;
    protected JCheckBox caldavNoSSLCheckBox;
    protected JTextField ldapPortField;
    protected JCheckBox ldapPortCheckBox;
    protected JCheckBox ldapNoSSLCheckBox;
    protected JTextField keepDelayField;
    protected JTextField sentKeepDelayField;
    protected JTextField caldavPastDelayField;
    protected JCheckBox caldavAutoScheduleCheckBox;
    protected JTextField imapIdleDelayField;

    protected JCheckBox useSystemProxiesField;
    protected JCheckBox enableProxyField;
    protected JTextField httpProxyField;
    protected JTextField httpProxyPortField;
    protected JTextField httpProxyUserField;
    protected JTextField httpProxyPasswordField;
    protected JTextField noProxyForField;

    protected JCheckBox allowRemoteField;
    protected JTextField bindAddressField;
    protected JTextField clientSoTimeoutField;
    protected JTextField certHashField;
    protected JCheckBox disableUpdateCheck;

    protected JComboBox<String> keystoreTypeCombo;
    protected JTextField keystoreFileField;
    protected JPasswordField keystorePassField;
    protected JPasswordField keyPassField;

    protected JComboBox<String> clientKeystoreTypeCombo;
    protected JTextField clientKeystoreFileField;
    protected JPasswordField clientKeystorePassField;
    protected JTextField pkcs11LibraryField;
    protected JTextArea pkcs11ConfigField;

    protected JComboBox<Level> rootLoggingLevelField;
    protected JComboBox<Level> davmailLoggingLevelField;
    protected JComboBox<Level> httpclientLoggingLevelField;
    protected JComboBox<Level> wireLoggingLevelField;
    protected JTextField logFilePathField;
    protected JTextField logFileSizeField;

    protected JCheckBox caldavEditNotificationsField;
    protected JTextField caldavAlarmSoundField;
    protected JCheckBox forceActiveSyncUpdateCheckBox;
    protected JTextField defaultDomainField;
    protected JCheckBox showStartupBannerCheckBox;
    protected JCheckBox disableGuiNotificationsCheckBox;
    protected JCheckBox disableTrayActivitySwitchCheckBox;

    protected JCheckBox imapAutoExpungeCheckBox;
    protected JCheckBox enableKeepAliveCheckBox;
    protected JCheckBox popMarkReadOnRetrCheckBox;
    protected JComboBox<String> modeComboBox;
    protected JCheckBox enableKerberosCheckBox;
    protected JTextField folderSizeLimitField;
    protected JCheckBox smtpSaveInSentCheckBox;
    protected JCheckBox imapAlwaysApproxMsgSizeCheckBox;

    protected JTextField oauthTenantIdField;
    protected JTextField oauthClientIdField;
    protected JTextField oauthRedirectUriField;

    JCheckBox osxHideFromDockCheckBox;

    protected void addSettingComponent(JPanel panel, String label, JComponent component) {
        addSettingComponent(panel, label, component, null);
    }

    protected JLabel buildFieldLabel(String label, String toolTipText) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        fieldLabel.setVerticalAlignment(SwingConstants.CENTER);
        if (toolTipText != null) {
            fieldLabel.setToolTipText(toolTipText);
        }
        return fieldLabel;
    }

    protected void addSettingComponent(JPanel panel, String label, JComponent component, String toolTipText) {
        panel.add(buildFieldLabel(label, toolTipText));

        component.setMaximumSize(component.getPreferredSize());
        JPanel innerPanel = new JPanel();
        innerPanel.setBorder(BorderFactory.createEmptyBorder(1,0,0,0));
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
        innerPanel.add(component);
        panel.add(innerPanel);
        if (toolTipText != null) {
            component.setToolTipText(toolTipText);
        }
    }

    protected void addPortSettingComponent(JPanel panel, String label, JComponent component, JComponent checkboxComponent, JComponent checkboxSSLComponent, String toolTipText) {
        panel.add(buildFieldLabel(label, toolTipText));
        component.setMaximumSize(component.getPreferredSize());
        JPanel innerPanel = new JPanel();
        innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
        innerPanel.add(checkboxComponent);
        innerPanel.add(component);
        innerPanel.add(checkboxSSLComponent);
        panel.add(innerPanel);
        if (toolTipText != null) {
            component.setToolTipText(toolTipText);
        }
    }

    protected JPanel getSettingsPanel() {
        JPanel settingsPanel = new JPanel(new GridLayout(7, 2));
        settingsPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_GATEWAY")));

        modeComboBox = new JComboBox<>();
        modeComboBox.addItem(Settings.EWS);
        modeComboBox.addItem(Settings.O365);
        modeComboBox.addItem(Settings.O365_MODERN);
        modeComboBox.addItem(Settings.O365_INTERACTIVE);
        modeComboBox.addItem(Settings.O365_MANUAL);
        modeComboBox.addItem(Settings.WEBDAV);
        modeComboBox.addItem(Settings.AUTO);
        modeComboBox.setSelectedItem(Settings.getProperty("davmail.mode", Settings.EWS));
        modeComboBox.addActionListener(evt -> {
            String selectedItem = (String)modeComboBox.getSelectedItem();
            modeComboBox.setToolTipText(BundleMessage.format("UI_"+selectedItem+"_HELP"));
            if (selectedItem != null && selectedItem.startsWith("O365")) {
                urlField.setEnabled(false);
                urlField.setText(Settings.getO365Url());
            } else {
                urlField.setEnabled(true);
            }
        });
        urlField = new JTextField(Settings.getProperty("davmail.url", Settings.getO365Url()), 20);
        popPortField = new JTextField(Settings.getProperty("davmail.popPort"), 4);
        popPortCheckBox = new JCheckBox();
        popNoSSLCheckBox = new JCheckBox(BundleMessage.format("UI_NO_SSL"), Settings.getBooleanProperty("davmail.ssl.nosecurepop"));
        popPortCheckBox.setSelected(Settings.getProperty("davmail.popPort") != null && !Settings.getProperty("davmail.popPort").isEmpty());
        popPortField.setEnabled(popPortCheckBox.isSelected());
        popNoSSLCheckBox.setEnabled(popPortCheckBox.isSelected() && isSslEnabled());
        popPortCheckBox.addActionListener(evt -> {
            popPortField.setEnabled(popPortCheckBox.isSelected());
            popNoSSLCheckBox.setEnabled(popPortCheckBox.isSelected() && isSslEnabled());
        });

        imapPortField = new JTextField(Settings.getProperty("davmail.imapPort"), 4);
        imapPortCheckBox = new JCheckBox();
        imapNoSSLCheckBox = new JCheckBox(BundleMessage.format("UI_NO_SSL"), Settings.getBooleanProperty("davmail.ssl.nosecureimap"));
        imapPortCheckBox.setSelected(Settings.getProperty("davmail.imapPort") != null && !Settings.getProperty("davmail.imapPort").isEmpty());
        imapPortField.setEnabled(imapPortCheckBox.isSelected());
        imapNoSSLCheckBox.setEnabled(imapPortCheckBox.isSelected() && isSslEnabled());
        imapPortCheckBox.addActionListener(evt -> {
            imapPortField.setEnabled(imapPortCheckBox.isSelected());
            imapNoSSLCheckBox.setEnabled(imapPortCheckBox.isSelected() && isSslEnabled());
        });

        smtpPortField = new JTextField(Settings.getProperty("davmail.smtpPort"), 4);
        smtpPortCheckBox = new JCheckBox();
        smtpNoSSLCheckBox = new JCheckBox(BundleMessage.format("UI_NO_SSL"), Settings.getBooleanProperty("davmail.ssl.nosecuresmtp"));
        smtpPortCheckBox.setSelected(Settings.getProperty("davmail.smtpPort") != null && !Settings.getProperty("davmail.smtpPort").isEmpty());
        smtpPortField.setEnabled(smtpPortCheckBox.isSelected());
        smtpNoSSLCheckBox.setEnabled(smtpPortCheckBox.isSelected() && isSslEnabled());
        smtpPortCheckBox.addActionListener(evt -> {
            smtpPortField.setEnabled(smtpPortCheckBox.isSelected());
            smtpNoSSLCheckBox.setEnabled(smtpPortCheckBox.isSelected() && isSslEnabled());
        });

        caldavPortField = new JTextField(Settings.getProperty("davmail.caldavPort"), 4);
        caldavPortCheckBox = new JCheckBox();
        caldavNoSSLCheckBox = new JCheckBox(BundleMessage.format("UI_NO_SSL"), Settings.getBooleanProperty("davmail.ssl.nosecurecaldav"));
        caldavPortCheckBox.setSelected(Settings.getProperty("davmail.caldavPort") != null && !Settings.getProperty("davmail.caldavPort").isEmpty());
        caldavPortField.setEnabled(caldavPortCheckBox.isSelected());
        caldavNoSSLCheckBox.setEnabled(caldavPortCheckBox.isSelected() && isSslEnabled());
        caldavPortCheckBox.addActionListener(evt -> {
            caldavPortField.setEnabled(caldavPortCheckBox.isSelected());
            caldavNoSSLCheckBox.setEnabled(caldavPortCheckBox.isSelected() && isSslEnabled());
        });

        ldapPortField = new JTextField(Settings.getProperty("davmail.ldapPort"), 4);
        ldapPortCheckBox = new JCheckBox();
        ldapNoSSLCheckBox = new JCheckBox(BundleMessage.format("UI_NO_SSL"), Settings.getBooleanProperty("davmail.ssl.nosecureldap"));
        ldapPortCheckBox.setSelected(Settings.getProperty("davmail.ldapPort") != null && !Settings.getProperty("davmail.ldapPort").isEmpty());
        ldapPortField.setEnabled(ldapPortCheckBox.isSelected());
        ldapNoSSLCheckBox.setEnabled(ldapPortCheckBox.isSelected() && isSslEnabled());
        ldapPortCheckBox.addActionListener(evt -> {
            ldapPortField.setEnabled(ldapPortCheckBox.isSelected());
            ldapNoSSLCheckBox.setEnabled(ldapPortCheckBox.isSelected() && isSslEnabled());
        });

        addSettingComponent(settingsPanel, BundleMessage.format("UI_ENABLE_EWS"), modeComboBox,
                BundleMessage.format("UI_ENABLE_EWS_HELP"));
        addSettingComponent(settingsPanel, BundleMessage.format("UI_OWA_URL"), urlField, BundleMessage.format("UI_OWA_URL_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_POP_PORT"), popPortField, popPortCheckBox,
                popNoSSLCheckBox, BundleMessage.format("UI_POP_PORT_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_IMAP_PORT"), imapPortField, imapPortCheckBox,
                imapNoSSLCheckBox, BundleMessage.format("UI_IMAP_PORT_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_SMTP_PORT"), smtpPortField, smtpPortCheckBox,
                smtpNoSSLCheckBox, BundleMessage.format("UI_SMTP_PORT_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_CALDAV_PORT"), caldavPortField, caldavPortCheckBox,
                caldavNoSSLCheckBox, BundleMessage.format("UI_CALDAV_PORT_HELP"));
        addPortSettingComponent(settingsPanel, BundleMessage.format("UI_LDAP_PORT"), ldapPortField, ldapPortCheckBox,
                ldapNoSSLCheckBox, BundleMessage.format("UI_LDAP_PORT_HELP"));
        return settingsPanel;
    }

    protected JPanel getDelaysPanel() {
        JPanel delaysPanel = new JPanel(new GridLayout(4, 2));
        delaysPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_DELAYS")));

        keepDelayField = new JTextField(Settings.getProperty("davmail.keepDelay"), 4);
        sentKeepDelayField = new JTextField(Settings.getProperty("davmail.sentKeepDelay"), 4);
        caldavPastDelayField = new JTextField(Settings.getProperty("davmail.caldavPastDelay"), 4);
        imapIdleDelayField = new JTextField(Settings.getProperty("davmail.imapIdleDelay"), 4);

        addSettingComponent(delaysPanel, BundleMessage.format("UI_KEEP_DELAY"), keepDelayField,
                BundleMessage.format("UI_KEEP_DELAY_HELP"));
        addSettingComponent(delaysPanel, BundleMessage.format("UI_SENT_KEEP_DELAY"), sentKeepDelayField,
                BundleMessage.format("UI_SENT_KEEP_DELAY_HELP"));
        addSettingComponent(delaysPanel, BundleMessage.format("UI_CALENDAR_PAST_EVENTS"), caldavPastDelayField,
                BundleMessage.format("UI_CALENDAR_PAST_EVENTS_HELP"));
        addSettingComponent(delaysPanel, BundleMessage.format("UI_IMAP_IDLE_DELAY"), imapIdleDelayField,
                BundleMessage.format("UI_IMAP_IDLE_DELAY_HELP"));
        return delaysPanel;
    }

    protected JPanel getProxyPanel() {
        JPanel proxyPanel = new JPanel(new GridLayout(7, 2));
        proxyPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_PROXY")));

        boolean useSystemProxies = Settings.getBooleanProperty("davmail.useSystemProxies", Boolean.FALSE);
        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        useSystemProxiesField = new JCheckBox();
        useSystemProxiesField.setSelected(useSystemProxies);
        enableProxyField = new JCheckBox();
        enableProxyField.setSelected(enableProxy);
        httpProxyField = new JTextField(Settings.getProperty("davmail.proxyHost"), 15);
        httpProxyPortField = new JTextField(Settings.getProperty("davmail.proxyPort"), 4);
        httpProxyUserField = new JTextField(Settings.getProperty("davmail.proxyUser"), 10);
        httpProxyPasswordField = new JPasswordField(Settings.getProperty("davmail.proxyPassword"), 10);
        noProxyForField = new JTextField(Settings.getProperty("davmail.noProxyFor"), 15);

        enableProxyField.setEnabled(!useSystemProxies);
        httpProxyField.setEnabled(enableProxy);
        httpProxyPortField.setEnabled(enableProxy);
        httpProxyUserField.setEnabled(enableProxy || useSystemProxies);
        httpProxyPasswordField.setEnabled(enableProxy || useSystemProxies);
        noProxyForField.setEnabled(enableProxy);

        useSystemProxiesField.addActionListener(evt -> {
            boolean newUseSystemProxies = useSystemProxiesField.isSelected();
            if (newUseSystemProxies) {
                enableProxyField.setSelected(false);
                enableProxyField.setEnabled(false);
                httpProxyField.setEnabled(false);
                httpProxyPortField.setEnabled(false);
                httpProxyUserField.setEnabled(true);
                httpProxyPasswordField.setEnabled(true);
                noProxyForField.setEnabled(false);
            } else {
                enableProxyField.setEnabled(true);
                httpProxyUserField.setEnabled(false);
                httpProxyPasswordField.setEnabled(false);
            }
        });
        enableProxyField.addActionListener(evt -> {
            boolean newEnableProxy = enableProxyField.isSelected();
            httpProxyField.setEnabled(newEnableProxy);
            httpProxyPortField.setEnabled(newEnableProxy);
            httpProxyUserField.setEnabled(newEnableProxy);
            httpProxyPasswordField.setEnabled(newEnableProxy);
            noProxyForField.setEnabled(newEnableProxy);
        });

        addSettingComponent(proxyPanel, BundleMessage.format("UI_USE_SYSTEM_PROXIES"), useSystemProxiesField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_ENABLE_PROXY"), enableProxyField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_PROXY_SERVER"), httpProxyField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_PROXY_PORT"), httpProxyPortField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_PROXY_USER"), httpProxyUserField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_PROXY_PASSWORD"), httpProxyPasswordField);
        addSettingComponent(proxyPanel, BundleMessage.format("UI_NO_PROXY"), noProxyForField);
        updateMaximumSize(proxyPanel);
        return proxyPanel;
    }

    protected JPanel getKeystorePanel() {
        JPanel keyStorePanel = new JPanel(new GridLayout(4, 2));
        keyStorePanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_DAVMAIL_SERVER_CERTIFICATE")));

        keystoreTypeCombo = new JComboBox<>(new String[]{"JKS", "PKCS12"});
        keystoreTypeCombo.setSelectedItem(Settings.getProperty("davmail.ssl.keystoreType"));
        keystoreFileField = new JTextField(Settings.getProperty("davmail.ssl.keystoreFile"), 20);
        keystorePassField = new JPasswordField(Settings.getProperty("davmail.ssl.keystorePass"), 20);
        keyPassField = new JPasswordField(Settings.getProperty("davmail.ssl.keyPass"), 20);

        addSettingComponent(keyStorePanel, BundleMessage.format("UI_KEY_STORE_TYPE"), keystoreTypeCombo,
                BundleMessage.format("UI_KEY_STORE_TYPE_HELP"));
        addSettingComponent(keyStorePanel, BundleMessage.format("UI_KEY_STORE"), keystoreFileField,
                BundleMessage.format("UI_KEY_STORE_HELP"));
        addSettingComponent(keyStorePanel, BundleMessage.format("UI_KEY_STORE_PASSWORD"), keystorePassField,
                BundleMessage.format("UI_KEY_STORE_PASSWORD_HELP"));
        addSettingComponent(keyStorePanel, BundleMessage.format("UI_KEY_PASSWORD"), keyPassField,
                BundleMessage.format("UI_KEY_PASSWORD_HELP"));
        updateMaximumSize(keyStorePanel);
        return keyStorePanel;
    }

    protected JPanel getSmartCardPanel() {
        JPanel clientKeystorePanel = new JPanel(new GridLayout(2, 1));
        clientKeystorePanel.setLayout(new BoxLayout(clientKeystorePanel, BoxLayout.Y_AXIS));
        clientKeystorePanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_CLIENT_CERTIFICATE")));

        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            clientKeystoreTypeCombo = new JComboBox<>(new String[]{"MSCAPI", "PKCS11", "JKS", "PKCS12"});
        } else {
            clientKeystoreTypeCombo = new JComboBox<>(new String[]{"PKCS11", "JKS", "PKCS12"});
        }
        clientKeystoreTypeCombo.setSelectedItem(Settings.getProperty("davmail.ssl.clientKeystoreType"));
        clientKeystoreFileField = new JTextField(Settings.getProperty("davmail.ssl.clientKeystoreFile"), 20);
        clientKeystorePassField = new JPasswordField(Settings.getProperty("davmail.ssl.clientKeystorePass"), 20);

        pkcs11LibraryField = new JTextField(Settings.getProperty("davmail.ssl.pkcs11Library"), 20);
        pkcs11ConfigField = new JTextArea(2, 20);
        pkcs11ConfigField.setText(Settings.getProperty("davmail.ssl.pkcs11Config"));
        pkcs11ConfigField.setBorder(pkcs11LibraryField.getBorder());
        pkcs11ConfigField.setFont(pkcs11LibraryField.getFont());

        JPanel clientKeystoreTypePanel = new JPanel(new GridLayout(1, 2));
        addSettingComponent(clientKeystoreTypePanel, BundleMessage.format("UI_CLIENT_KEY_STORE_TYPE"), clientKeystoreTypeCombo,
                BundleMessage.format("UI_CLIENT_KEY_STORE_TYPE_HELP"));
        clientKeystorePanel.add(clientKeystoreTypePanel);

        final JPanel cardPanel = new JPanel(new CardLayout());
        clientKeystorePanel.add(cardPanel);

        JPanel clientKeystoreFilePanel = new JPanel(new GridLayout(2, 2));
        addSettingComponent(clientKeystoreFilePanel, BundleMessage.format("UI_CLIENT_KEY_STORE"), clientKeystoreFileField,
                BundleMessage.format("UI_CLIENT_KEY_STORE_HELP"));
        addSettingComponent(clientKeystoreFilePanel, BundleMessage.format("UI_CLIENT_KEY_STORE_PASSWORD"), clientKeystorePassField,
                BundleMessage.format("UI_CLIENT_KEY_STORE_PASSWORD_HELP"));
        JPanel wrapperPanel = new JPanel();
        wrapperPanel.add(clientKeystoreFilePanel);
        cardPanel.add(wrapperPanel, "FILE");

        JPanel pkcs11Panel = new JPanel(new GridLayout(2, 2));
        addSettingComponent(pkcs11Panel, BundleMessage.format("UI_PKCS11_LIBRARY"), pkcs11LibraryField,
                BundleMessage.format("UI_PKCS11_LIBRARY_HELP"));
        addSettingComponent(pkcs11Panel, BundleMessage.format("UI_PKCS11_CONFIG"), pkcs11ConfigField,
                BundleMessage.format("UI_PKCS11_CONFIG_HELP"));
        cardPanel.add(pkcs11Panel, "PKCS11");

        ((CardLayout) cardPanel.getLayout()).show(cardPanel, (String) clientKeystoreTypeCombo.getSelectedItem());

        clientKeystoreTypeCombo.addItemListener(event -> {
            CardLayout cardLayout = (CardLayout) (cardPanel.getLayout());
            if ("PKCS11".equals(event.getItem())) {
                cardLayout.show(cardPanel, "PKCS11");
            } else {
                cardLayout.show(cardPanel, "FILE");
            }
        });
        updateMaximumSize(clientKeystorePanel);
        return clientKeystorePanel;
    }

    protected JPanel getOauthPanel() {
        JPanel oAuthPanel = new JPanel(new GridLayout(3, 2));
        oAuthPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_OAUTH")));

        oauthTenantIdField = new JTextField(Settings.getProperty("davmail.oauth.tenantId"), 20);
        oauthClientIdField = new JTextField(Settings.getProperty("davmail.oauth.clientId"), 20);
        oauthRedirectUriField = new JTextField(Settings.getProperty("davmail.oauth.redirectUri"), 20);

        addSettingComponent(oAuthPanel, BundleMessage.format("UI_OAUTH_TENANTID"), oauthTenantIdField,
                BundleMessage.format("UI_OAUTH_TENANTID_HELP"));
        addSettingComponent(oAuthPanel, BundleMessage.format("UI_OAUTH_CLIENTID"), oauthClientIdField,
                BundleMessage.format("UI_OAUTH_CLIENTID_HELP"));
        addSettingComponent(oAuthPanel, BundleMessage.format("UI_OAUTH_REDIRECTURI"), oauthRedirectUriField,
                BundleMessage.format("UI_OAUTH_REDIRECTURI_HELP"));
        updateMaximumSize(oAuthPanel);
        return oAuthPanel;
    }

    protected JPanel getNetworkSettingsPanel() {
        JPanel networkSettingsPanel = new JPanel(new GridLayout(4, 2));
        networkSettingsPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_NETWORK")));

        allowRemoteField = new JCheckBox();
        allowRemoteField.setSelected(Settings.getBooleanProperty("davmail.allowRemote"));

        bindAddressField = new JTextField(Settings.getProperty("davmail.bindAddress"), 15);
        clientSoTimeoutField = new JTextField(Settings.getProperty("davmail.clientSoTimeout"), 15);

        certHashField = new JTextField(Settings.getProperty("davmail.server.certificate.hash"), 15);

        addSettingComponent(networkSettingsPanel, BundleMessage.format("UI_BIND_ADDRESS"), bindAddressField,
                BundleMessage.format("UI_BIND_ADDRESS_HELP"));
        addSettingComponent(networkSettingsPanel, BundleMessage.format("UI_CLIENT_SO_TIMEOUT"), clientSoTimeoutField,
                BundleMessage.format("UI_CLIENT_SO_TIMEOUT_HELP"));
        addSettingComponent(networkSettingsPanel, BundleMessage.format("UI_ALLOW_REMOTE_CONNECTION"), allowRemoteField,
                BundleMessage.format("UI_ALLOW_REMOTE_CONNECTION_HELP"));
        addSettingComponent(networkSettingsPanel, BundleMessage.format("UI_SERVER_CERTIFICATE_HASH"), certHashField,
                BundleMessage.format("UI_SERVER_CERTIFICATE_HASH_HELP"));
        updateMaximumSize(networkSettingsPanel);
        return networkSettingsPanel;
    }

    protected JPanel getOtherSettingsPanel() {
        JPanel otherSettingsPanel = new JPanel(new GridLayout(16, 2));
        otherSettingsPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_OTHER")));

        folderSizeLimitField = new JTextField(Settings.getProperty("davmail.folderSizeLimit"), 6);
        enableKerberosCheckBox = new JCheckBox();
        enableKerberosCheckBox.setSelected(Settings.getBooleanProperty("davmail.enableKerberos"));
        caldavEditNotificationsField = new JCheckBox();
        caldavEditNotificationsField.setSelected(Settings.getBooleanProperty("davmail.caldavEditNotifications"));
        caldavAlarmSoundField = new JTextField(Settings.getProperty("davmail.caldavAlarmSound"), 15);
        forceActiveSyncUpdateCheckBox = new JCheckBox();
        forceActiveSyncUpdateCheckBox.setSelected(Settings.getBooleanProperty("davmail.forceActiveSyncUpdate"));
        defaultDomainField = new JTextField(Settings.getProperty("davmail.defaultDomain"), 15);
        showStartupBannerCheckBox = new JCheckBox();
        showStartupBannerCheckBox.setSelected(Settings.getBooleanProperty("davmail.showStartupBanner", true));
        disableGuiNotificationsCheckBox = new JCheckBox();
        disableGuiNotificationsCheckBox.setSelected(Settings.getBooleanProperty("davmail.disableGuiNotifications", false));
        disableTrayActivitySwitchCheckBox =  new JCheckBox();
        disableTrayActivitySwitchCheckBox.setSelected(Settings.getBooleanProperty("davmail.disableTrayActivitySwitch", false));
        imapAutoExpungeCheckBox = new JCheckBox();
        imapAutoExpungeCheckBox.setSelected(Settings.getBooleanProperty("davmail.imapAutoExpunge", true));
        imapAlwaysApproxMsgSizeCheckBox = new JCheckBox();
        imapAlwaysApproxMsgSizeCheckBox.setSelected(Settings.getBooleanProperty("davmail.imapAlwaysApproxMsgSize", false));
        enableKeepAliveCheckBox = new JCheckBox();
        enableKeepAliveCheckBox.setSelected(Settings.getBooleanProperty("davmail.enableKeepAlive", false));
        popMarkReadOnRetrCheckBox = new JCheckBox();
        popMarkReadOnRetrCheckBox.setSelected(Settings.getBooleanProperty("davmail.popMarkReadOnRetr", false));
        smtpSaveInSentCheckBox = new JCheckBox();
        smtpSaveInSentCheckBox.setSelected(Settings.getBooleanProperty("davmail.smtpSaveInSent", true));
        disableUpdateCheck = new JCheckBox();
        disableUpdateCheck.setSelected(Settings.getBooleanProperty("davmail.disableUpdateCheck"));
        caldavAutoScheduleCheckBox = new JCheckBox();
        caldavAutoScheduleCheckBox.setSelected(Settings.getBooleanProperty("davmail.caldavAutoSchedule"));

        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_FOLDER_SIZE_LIMIT"), folderSizeLimitField,
                BundleMessage.format("UI_FOLDER_SIZE_LIMIT_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_ENABLE_KERBEROS"), enableKerberosCheckBox,
                BundleMessage.format("UI_ENABLE_KERBEROS_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_CALDAV_EDIT_NOTIFICATIONS"), caldavEditNotificationsField,
                BundleMessage.format("UI_CALDAV_EDIT_NOTIFICATIONS_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_CALDAV_ALARM_SOUND"), caldavAlarmSoundField,
                BundleMessage.format("UI_CALDAV_ALARM_SOUND_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_FORCE_ACTIVESYNC_UPDATE"), forceActiveSyncUpdateCheckBox,
                BundleMessage.format("UI_FORCE_ACTIVESYNC_UPDATE_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_DEFAULT_DOMAIN"), defaultDomainField,
                BundleMessage.format("UI_DEFAULT_DOMAIN_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_SHOW_STARTUP_BANNER"), showStartupBannerCheckBox,
                BundleMessage.format("UI_SHOW_STARTUP_BANNER_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_DISABLE_GUI_NOTIFICATIONS"), disableGuiNotificationsCheckBox,
                BundleMessage.format("UI_DISABLE_GUI_NOTIFICATIONS_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_DISABLE_TRAY_ACTIVITY_SWITCH"), disableTrayActivitySwitchCheckBox,
                BundleMessage.format("UI_DISABLE_TRAY_ACTIVITY_SWITCH_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_IMAP_AUTO_EXPUNGE"), imapAutoExpungeCheckBox,
                BundleMessage.format("UI_IMAP_AUTO_EXPUNGE_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_ALWAYS_APPROXIMATE_MSG_SIZE"), imapAlwaysApproxMsgSizeCheckBox,
                BundleMessage.format("UI_ALWAYS_APPROXIMATE_MSG_SIZE_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_ENABLE_KEEPALIVE"), enableKeepAliveCheckBox,
                BundleMessage.format("UI_ENABLE_KEEPALIVE_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_POP_MARK_READ"), popMarkReadOnRetrCheckBox,
                BundleMessage.format("UI_POP_MARK_READ_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_SAVE_IN_SENT"), smtpSaveInSentCheckBox,
                BundleMessage.format("UI_SAVE_IN_SENT_HELP"));
        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_DISABLE_UPDATE_CHECK"), disableUpdateCheck,
                BundleMessage.format("UI_DISABLE_UPDATE_CHECK_HELP"));

        addSettingComponent(otherSettingsPanel, BundleMessage.format("UI_CALDAV_AUTO_SCHEDULE"), caldavAutoScheduleCheckBox,
                BundleMessage.format("UI_CALDAV_AUTO_SCHEDULE_HELP"));

        updateMaximumSize(otherSettingsPanel);
        return otherSettingsPanel;
    }

    protected JPanel getOSXPanel() {
        JPanel osxSettingsPanel = new JPanel(new GridLayout(1, 2));
        osxSettingsPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_OSX")));

        osxHideFromDockCheckBox = new JCheckBox();
        osxHideFromDockCheckBox.setSelected(OSXInfoPlist.isHideFromDock());

        addSettingComponent(osxSettingsPanel, BundleMessage.format("UI_OSX_HIDE_FROM_DOCK"), osxHideFromDockCheckBox,
                BundleMessage.format("UI_OSX_HIDE_FROM_DOCK_HELP"));

        updateMaximumSize(osxSettingsPanel);
        return osxSettingsPanel;
    }

    protected JPanel getLoggingSettingsPanel() {
        JPanel loggingLevelPanel = new JPanel();
        JPanel leftLoggingPanel = new JPanel(new GridLayout(2, 2));
        JPanel rightLoggingPanel = new JPanel(new GridLayout(2, 2));
        loggingLevelPanel.add(leftLoggingPanel);
        loggingLevelPanel.add(rightLoggingPanel);

        rootLoggingLevelField = new JComboBox<>(LOG_LEVELS);
        davmailLoggingLevelField = new JComboBox<>(LOG_LEVELS);
        httpclientLoggingLevelField = new JComboBox<>(LOG_LEVELS);
        wireLoggingLevelField = new JComboBox<>(LOG_LEVELS);
        logFilePathField = new JTextField(Settings.getProperty("davmail.logFilePath"), 15);
        logFileSizeField = new JTextField(Settings.getProperty("davmail.logFileSize"), 15);

        rootLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("rootLogger"));
        davmailLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("davmail"));
        httpclientLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("httpclient"));
        wireLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("httpclient.wire"));

        addSettingComponent(leftLoggingPanel, BundleMessage.format("UI_LOG_DEFAULT"), rootLoggingLevelField);
        addSettingComponent(leftLoggingPanel, BundleMessage.format("UI_LOG_DAVMAIL"), davmailLoggingLevelField);
        addSettingComponent(rightLoggingPanel, BundleMessage.format("UI_LOG_HTTPCLIENT"), httpclientLoggingLevelField);
        addSettingComponent(rightLoggingPanel, BundleMessage.format("UI_LOG_WIRE"), wireLoggingLevelField);

        JPanel logFilePathPanel = new JPanel(new GridLayout(2, 2));
        addSettingComponent(logFilePathPanel, BundleMessage.format("UI_LOG_FILE_PATH"), logFilePathField);
        addSettingComponent(logFilePathPanel, BundleMessage.format("UI_LOG_FILE_SIZE"), logFileSizeField, BundleMessage.format("UI_LOG_FILE_SIZE_HELP"));

        JButton defaultButton = new JButton(BundleMessage.format("UI_BUTTON_DEFAULT"));
        defaultButton.setToolTipText(BundleMessage.format("UI_BUTTON_DEFAULT_HELP"));
        defaultButton.addActionListener(e -> {
            rootLoggingLevelField.setSelectedItem(Level.WARN);
            davmailLoggingLevelField.setSelectedItem(Level.DEBUG);
            httpclientLoggingLevelField.setSelectedItem(Level.WARN);
            wireLoggingLevelField.setSelectedItem(Level.WARN);
        });

        JPanel loggingPanel = new JPanel();
        loggingPanel.setLayout(new BoxLayout(loggingPanel, BoxLayout.Y_AXIS));
        loggingPanel.setBorder(BorderFactory.createTitledBorder(BundleMessage.format("UI_LOGGING_LEVELS")));
        loggingPanel.add(logFilePathPanel);
        loggingPanel.add(loggingLevelPanel);
        loggingPanel.add(defaultButton);

        updateMaximumSize(loggingPanel);
        return loggingPanel;
    }

    protected void updateMaximumSize(JPanel panel) {
        Dimension preferredSize = panel.getPreferredSize();
        preferredSize.width = Integer.MAX_VALUE;
        panel.setMaximumSize(preferredSize);
    }

    /**
     * Reload settings from properties.
     */
    public void reload() {
        // reload settings in form
        urlField.setText(Settings.getProperty("davmail.url", Settings.getO365Url()));
        popPortField.setText(Settings.getProperty("davmail.popPort"));
        popPortCheckBox.setSelected(Settings.getProperty("davmail.popPort") != null && !Settings.getProperty("davmail.popPort").isEmpty());
        popNoSSLCheckBox.setSelected(Settings.getBooleanProperty("davmail.ssl.nosecurepop"));
        imapPortField.setText(Settings.getProperty("davmail.imapPort"));
        imapPortCheckBox.setSelected(Settings.getProperty("davmail.imapPort") != null && !Settings.getProperty("davmail.imapPort").isEmpty());
        imapNoSSLCheckBox.setSelected(Settings.getBooleanProperty("davmail.ssl.nosecureimap"));
        smtpPortField.setText(Settings.getProperty("davmail.smtpPort"));
        smtpPortCheckBox.setSelected(Settings.getProperty("davmail.smtpPort") != null && !Settings.getProperty("davmail.smtpPort").isEmpty());
        smtpNoSSLCheckBox.setSelected(Settings.getBooleanProperty("davmail.ssl.nosecuresmtp"));
        caldavPortField.setText(Settings.getProperty("davmail.caldavPort"));
        caldavPortCheckBox.setSelected(Settings.getProperty("davmail.caldavPort") != null && !Settings.getProperty("davmail.caldavPort").isEmpty());
        caldavNoSSLCheckBox.setSelected(Settings.getBooleanProperty("davmail.ssl.nosecurecaldav"));
        ldapPortField.setText(Settings.getProperty("davmail.ldapPort"));
        ldapPortCheckBox.setSelected(Settings.getProperty("davmail.ldapPort") != null && !Settings.getProperty("davmail.ldapPort").isEmpty());
        ldapNoSSLCheckBox.setSelected(Settings.getBooleanProperty("davmail.ssl.nosecureldap"));
        keepDelayField.setText(Settings.getProperty("davmail.keepDelay"));
        sentKeepDelayField.setText(Settings.getProperty("davmail.sentKeepDelay"));
        caldavPastDelayField.setText(Settings.getProperty("davmail.caldavPastDelay"));
        imapIdleDelayField.setText(Settings.getProperty("davmail.imapIdleDelay"));
        boolean useSystemProxies = Settings.getBooleanProperty("davmail.useSystemProxies", Boolean.FALSE);
        useSystemProxiesField.setSelected(useSystemProxies);
        boolean enableProxy = Settings.getBooleanProperty("davmail.enableProxy");
        enableProxyField.setSelected(enableProxy);
        enableProxyField.setEnabled(!useSystemProxies);
        httpProxyField.setEnabled(enableProxy);
        httpProxyPortField.setEnabled(enableProxy);
        httpProxyUserField.setEnabled(useSystemProxies || enableProxy);
        httpProxyPasswordField.setEnabled(useSystemProxies || enableProxy);
        noProxyForField.setEnabled(enableProxy);
        httpProxyField.setText(Settings.getProperty("davmail.proxyHost"));
        httpProxyPortField.setText(Settings.getProperty("davmail.proxyPort"));
        httpProxyUserField.setText(Settings.getProperty("davmail.proxyUser"));
        httpProxyPasswordField.setText(Settings.getProperty("davmail.proxyPassword"));
        noProxyForField.setText(Settings.getProperty("davmail.noProxyFor"));

        bindAddressField.setText(Settings.getProperty("davmail.bindAddress"));
        allowRemoteField.setSelected(Settings.getBooleanProperty(("davmail.allowRemote")));
        certHashField.setText(Settings.getProperty("davmail.server.certificate.hash"));
        disableUpdateCheck.setSelected(Settings.getBooleanProperty(("davmail.disableUpdateCheck")));

        caldavEditNotificationsField.setSelected(Settings.getBooleanProperty("davmail.caldavEditNotifications"));
        clientSoTimeoutField.setText(Settings.getProperty("davmail.clientSoTimeout"));
        caldavAlarmSoundField.setText(Settings.getProperty("davmail.caldavAlarmSound"));
        forceActiveSyncUpdateCheckBox.setSelected(Settings.getBooleanProperty("davmail.forceActiveSyncUpdate"));
        defaultDomainField.setText(Settings.getProperty("davmail.defaultDomain"));
        showStartupBannerCheckBox.setSelected(Settings.getBooleanProperty("davmail.showStartupBanner", true));
        disableGuiNotificationsCheckBox.setSelected(Settings.getBooleanProperty("davmail.disableGuiNotifications", false));
        disableTrayActivitySwitchCheckBox.setSelected(Settings.getBooleanProperty("davmail.disableTrayActivitySwitch", false));
        imapAutoExpungeCheckBox.setSelected(Settings.getBooleanProperty("davmail.imapAutoExpunge", true));
        imapAlwaysApproxMsgSizeCheckBox.setSelected(Settings.getBooleanProperty("davmail.imapAlwaysApproxMsgSize", false));
        enableKeepAliveCheckBox.setSelected(Settings.getBooleanProperty("davmail.enableKeepAlive", false));
        popMarkReadOnRetrCheckBox.setSelected(Settings.getBooleanProperty("davmail.popMarkReadOnRetr", false));
        modeComboBox.setSelectedItem(Settings.getProperty("davmail.mode", Settings.EWS));
        smtpSaveInSentCheckBox.setSelected(Settings.getBooleanProperty("davmail.smtpSaveInSent", true));
        enableKerberosCheckBox.setSelected(Settings.getBooleanProperty("davmail.enableKerberos", false));
        folderSizeLimitField.setText(Settings.getProperty("davmail.folderSizeLimit"));

        keystoreTypeCombo.setSelectedItem(Settings.getProperty("davmail.ssl.keystoreType"));
        keystoreFileField.setText(Settings.getProperty("davmail.ssl.keystoreFile"));
        keystorePassField.setText(Settings.getProperty("davmail.ssl.keystorePass"));
        keyPassField.setText(Settings.getProperty("davmail.ssl.keyPass"));

        clientKeystoreTypeCombo.setSelectedItem(Settings.getProperty("davmail.ssl.clientKeystoreType"));
        pkcs11LibraryField.setText(Settings.getProperty("davmail.ssl.pkcs11Library"));
        pkcs11ConfigField.setText(Settings.getProperty("davmail.ssl.pkcs11Config"));

        oauthTenantIdField.setText(Settings.getProperty("davmail.oauth.tenantId"));
        oauthClientIdField.setText(Settings.getProperty("davmail.oauth.clientId"));
        oauthRedirectUriField.setText(Settings.getProperty("davmail.oauth.redirectUri"));

        rootLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("rootLogger"));
        davmailLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("davmail"));
        httpclientLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("httpclient"));
        wireLoggingLevelField.setSelectedItem(Settings.getLoggingLevel("httpclient.wire"));
        logFilePathField.setText(Settings.getProperty("davmail.logFilePath"));
        logFileSizeField.setText(Settings.getProperty("davmail.logFileSize"));

        if (osxHideFromDockCheckBox != null) {
            osxHideFromDockCheckBox.setSelected(OSXInfoPlist.isHideFromDock());
        }
    }

    protected boolean isSslEnabled() {
        if (keystoreFileField != null) {
            return !keystoreFileField.getText().isEmpty();
        } else {
            return Settings.getProperty("davmail.ssl.keystoreFile") != null &&
                    (!Settings.getProperty("davmail.ssl.keystoreFile").isEmpty());
        }
    }

    /**
     * DavMail settings frame.
     */
    public SettingsFrame() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setTitle(BundleMessage.format("UI_DAVMAIL_SETTINGS"));
        try {
            setIconImages(DavGatewayTray.getFrameIcons());
        } catch (NoSuchMethodError error) {
            DavGatewayTray.debug(new BundleMessage("LOG_UNABLE_TO_SET_ICON_IMAGE"));
        }

        JTabbedPane tabbedPane = new JTabbedPane();
        // add help (F1 handler)
        tabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F1"),
                "help");
        tabbedPane.getActionMap().put("help", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                DesktopBrowser.browse("https://davmail.sourceforge.net");
            }
        });
        tabbedPane.addChangeListener(e -> {
            boolean isSslEnabled = isSslEnabled();
            popNoSSLCheckBox.setEnabled(Settings.getProperty("davmail.popPort") != null && isSslEnabled);
            imapNoSSLCheckBox.setEnabled(imapPortCheckBox.isSelected() && isSslEnabled);
            smtpNoSSLCheckBox.setEnabled(smtpPortCheckBox.isSelected() && isSslEnabled);
            caldavNoSSLCheckBox.setEnabled(caldavPortCheckBox.isSelected() && isSslEnabled);
            ldapNoSSLCheckBox.setEnabled(ldapPortCheckBox.isSelected() && isSslEnabled);
        });

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(getSettingsPanel());
        mainPanel.add(getDelaysPanel());
        mainPanel.add(Box.createVerticalGlue());

        tabbedPane.add(BundleMessage.format("UI_TAB_MAIN"), mainPanel);

        JPanel proxyPanel = new JPanel();
        proxyPanel.setLayout(new BoxLayout(proxyPanel, BoxLayout.Y_AXIS));
        proxyPanel.add(getProxyPanel());
        proxyPanel.add(getNetworkSettingsPanel());
        tabbedPane.add(BundleMessage.format("UI_TAB_NETWORK"), proxyPanel);

        JPanel encryptionPanel = new JPanel();
        encryptionPanel.setLayout(new BoxLayout(encryptionPanel, BoxLayout.Y_AXIS));
        encryptionPanel.add(getKeystorePanel());
        encryptionPanel.add(getSmartCardPanel());
        encryptionPanel.add(getOauthPanel());
        // empty panel
        encryptionPanel.add(new JPanel());
        tabbedPane.add(BundleMessage.format("UI_TAB_ENCRYPTION"), encryptionPanel);

        JPanel loggingPanel = new JPanel();
        loggingPanel.setLayout(new BoxLayout(loggingPanel, BoxLayout.Y_AXIS));
        loggingPanel.add(getLoggingSettingsPanel());
        // empty panel
        loggingPanel.add(new JPanel());

        tabbedPane.add(BundleMessage.format("UI_TAB_LOGGING"), loggingPanel);

        JPanel advancedPanel = new JPanel();
        advancedPanel.setLayout(new BoxLayout(advancedPanel, BoxLayout.Y_AXIS));

        advancedPanel.add(getOtherSettingsPanel());
        // empty panel
        advancedPanel.add(new JPanel());

        tabbedPane.add(BundleMessage.format("UI_TAB_ADVANCED"), advancedPanel);

        if (OSXInfoPlist.isOSX()) {
            JPanel osxPanel = new JPanel();
            osxPanel.setLayout(new BoxLayout(osxPanel, BoxLayout.Y_AXIS));
            osxPanel.add(getOSXPanel());
            // empty panel
            osxPanel.add(new JPanel());

            tabbedPane.add(BundleMessage.format("UI_TAB_OSX"), osxPanel);
        }

        add(BorderLayout.CENTER, tabbedPane);

        JPanel buttonPanel = new JPanel();
        JButton cancel = new JButton(BundleMessage.format("UI_BUTTON_CANCEL"));
        JButton ok = new JButton(BundleMessage.format("UI_BUTTON_SAVE"));
        JButton help = new JButton(BundleMessage.format("UI_BUTTON_HELP"));
        ActionListener save = evt -> {
            // save options
            Settings.setProperty("davmail.url", urlField.getText().trim());
            Settings.setProperty("davmail.popPort", popPortCheckBox.isSelected() ? popPortField.getText() : "");
            Settings.setProperty("davmail.ssl.nosecurepop", String.valueOf(popNoSSLCheckBox.isSelected()));
            Settings.setProperty("davmail.imapPort", imapPortCheckBox.isSelected() ? imapPortField.getText() : "");
            Settings.setProperty("davmail.ssl.nosecureimap", String.valueOf(imapNoSSLCheckBox.isSelected()));
            Settings.setProperty("davmail.smtpPort", smtpPortCheckBox.isSelected() ? smtpPortField.getText() : "");
            Settings.setProperty("davmail.ssl.nosecuresmtp", String.valueOf(smtpNoSSLCheckBox.isSelected()));
            Settings.setProperty("davmail.caldavPort", caldavPortCheckBox.isSelected() ? caldavPortField.getText() : "");
            Settings.setProperty("davmail.ssl.nosecurecaldav", String.valueOf(caldavNoSSLCheckBox.isSelected()));
            Settings.setProperty("davmail.ldapPort", ldapPortCheckBox.isSelected() ? ldapPortField.getText() : "");
            Settings.setProperty("davmail.ssl.nosecureldap", String.valueOf(ldapNoSSLCheckBox.isSelected()));
            Settings.setProperty("davmail.keepDelay", keepDelayField.getText());
            Settings.setProperty("davmail.sentKeepDelay", sentKeepDelayField.getText());
            Settings.setProperty("davmail.caldavPastDelay", caldavPastDelayField.getText());
            Settings.setProperty("davmail.imapIdleDelay", imapIdleDelayField.getText());
            Settings.setProperty("davmail.useSystemProxies", String.valueOf(useSystemProxiesField.isSelected()));
            Settings.setProperty("davmail.enableProxy", String.valueOf(enableProxyField.isSelected()));
            Settings.setProperty("davmail.proxyHost", httpProxyField.getText());
            Settings.setProperty("davmail.proxyPort", httpProxyPortField.getText());
            Settings.setProperty("davmail.proxyUser", httpProxyUserField.getText());
            Settings.setProperty("davmail.proxyPassword", httpProxyPasswordField.getText());
            Settings.setProperty("davmail.noProxyFor", noProxyForField.getText());

            Settings.setProperty("davmail.bindAddress", bindAddressField.getText());
            Settings.setProperty("davmail.clientSoTimeout", String.valueOf(clientSoTimeoutField.getText()));
            Settings.setProperty("davmail.allowRemote", String.valueOf(allowRemoteField.isSelected()));
            Settings.setProperty("davmail.server.certificate.hash", certHashField.getText());
            Settings.setProperty("davmail.disableUpdateCheck", String.valueOf(disableUpdateCheck.isSelected()));
            Settings.setProperty("davmail.caldavAutoSchedule", String.valueOf(caldavAutoScheduleCheckBox.isSelected()));

            Settings.setProperty("davmail.caldavEditNotifications", String.valueOf(caldavEditNotificationsField.isSelected()));
            Settings.setProperty("davmail.caldavAlarmSound", String.valueOf(caldavAlarmSoundField.getText()));
            Settings.setProperty("davmail.forceActiveSyncUpdate", String.valueOf(forceActiveSyncUpdateCheckBox.isSelected()));
            Settings.setProperty("davmail.defaultDomain", String.valueOf(defaultDomainField.getText()));
            Settings.setProperty("davmail.showStartupBanner", String.valueOf(showStartupBannerCheckBox.isSelected()));
            Settings.setProperty("davmail.disableGuiNotifications", String.valueOf(disableGuiNotificationsCheckBox.isSelected()));
            Settings.setProperty("davmail.disableTrayActivitySwitch", String.valueOf(disableTrayActivitySwitchCheckBox.isSelected()));
            Settings.setProperty("davmail.imapAutoExpunge", String.valueOf(imapAutoExpungeCheckBox.isSelected()));
            Settings.setProperty("davmail.imapAlwaysApproxMsgSize", String.valueOf(imapAlwaysApproxMsgSizeCheckBox.isSelected()));
            Settings.setProperty("davmail.enableKeepAlive", String.valueOf(enableKeepAliveCheckBox.isSelected()));
            Settings.setProperty("davmail.popMarkReadOnRetr", String.valueOf(popMarkReadOnRetrCheckBox.isSelected()));

            Settings.setProperty("davmail.mode", (String) modeComboBox.getSelectedItem());
            Settings.setProperty("davmail.enableKerberos", String.valueOf(enableKerberosCheckBox.isSelected()));
            Settings.setProperty("davmail.folderSizeLimit", folderSizeLimitField.getText());
            Settings.setProperty("davmail.smtpSaveInSent", String.valueOf(smtpSaveInSentCheckBox.isSelected()));

            Settings.setProperty("davmail.ssl.keystoreType", (String) keystoreTypeCombo.getSelectedItem());
            Settings.setProperty("davmail.ssl.keystoreFile", keystoreFileField.getText());
            Settings.setProperty("davmail.ssl.keystorePass", String.valueOf(keystorePassField.getPassword()));
            Settings.setProperty("davmail.ssl.keyPass", String.valueOf(keyPassField.getPassword()));

            Settings.setProperty("davmail.ssl.clientKeystoreType", (String) clientKeystoreTypeCombo.getSelectedItem());
            Settings.setProperty("davmail.ssl.clientKeystoreFile", clientKeystoreFileField.getText());
            Settings.setProperty("davmail.ssl.clientKeystorePass", String.valueOf(clientKeystorePassField.getPassword()));
            Settings.setProperty("davmail.ssl.pkcs11Library", pkcs11LibraryField.getText());
            Settings.setProperty("davmail.ssl.pkcs11Config", pkcs11ConfigField.getText());

            Settings.setProperty("davmail.oauth.tenantId", oauthTenantIdField.getText());
            Settings.setProperty("davmail.oauth.clientId", oauthClientIdField.getText());
            Settings.setProperty("davmail.oauth.redirectUri", oauthRedirectUriField.getText());

            Settings.setLoggingLevel("rootLogger", (Level) rootLoggingLevelField.getSelectedItem());
            Settings.setLoggingLevel("davmail", (Level) davmailLoggingLevelField.getSelectedItem());
            Settings.setLoggingLevel("httpclient", (Level) httpclientLoggingLevelField.getSelectedItem());
            Settings.setLoggingLevel("httpclient.wire", (Level) wireLoggingLevelField.getSelectedItem());
            Settings.setProperty("davmail.logFilePath", logFilePathField.getText());
            Settings.setProperty("davmail.logFileSize", logFileSizeField.getText());

            setVisible(false);
            Settings.save();

            if (osxHideFromDockCheckBox != null) {
                OSXInfoPlist.setOSXHideFromDock(osxHideFromDockCheckBox.isSelected());
            }

            // restart listeners with new config
            DavGateway.restart();
        };
        ok.addActionListener(save);

        cancel.addActionListener(evt -> {
            reload();
            setVisible(false);
        });

        help.addActionListener(e -> DesktopBrowser.browse("https://davmail.sourceforge.net"));

        buttonPanel.add(ok);
        buttonPanel.add(cancel);
        buttonPanel.add(help);

        add(BorderLayout.SOUTH, buttonPanel);

        pack();
        //setResizable(false);
        // center frame
        setLocationRelativeTo(null);
        urlField.requestFocus();
    }
}
