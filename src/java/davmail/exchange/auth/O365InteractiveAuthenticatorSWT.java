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

package davmail.exchange.auth;

import davmail.BundleMessage;
import davmail.Settings;
import davmail.ui.tray.SwtGatewayTray;
import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Alternative embedded browser implementation based on SWT instead of OpenJFX
 */
public class O365InteractiveAuthenticatorSWT {

    private static final Logger LOGGER = Logger.getLogger(O365InteractiveAuthenticatorSWT.class);

    private O365InteractiveAuthenticator authenticator;

    Shell shell;
    Browser browser;

    public O365InteractiveAuthenticatorSWT() {

    }

    public void setO365InteractiveAuthenticator(O365InteractiveAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public void authenticate(final String initUrl, final String redirectUri) {
        // initialize SWT
        SwtGatewayTray.initDisplay();

        // allow SSO on windows
        System.setProperty("org.eclipse.swt.browser.Edge.allowSingleSignOnUsingOSPrimaryAccount",
                Settings.getProperty("davmail.oauth.allowSingleSignOnUsingOSPrimaryAccount", "true"));

        Display.getDefault().asyncExec(() -> {
            try {
                shell = new Shell(Display.getDefault());
                shell.setText(BundleMessage.format("UI_DAVMAIL_GATEWAY"));
                shell.setSize(600, 600);

                shell.setLayout(new FillLayout());

                shell.setImage(SwtGatewayTray.loadSwtImage("tray.png", 32));

                shell.addListener(SWT.Close, event -> {
                    if (!authenticator.isAuthenticated && authenticator.errorCode == null) {
                        authenticator.errorCode = "user closed authentication window";
                    }
                    dispose();
                });

                browser = new Browser(shell, SWT.NONE);

                browser.setUrl(initUrl);
                browser.addTitleListener(titleEvent -> shell.setText("DavMail: " + titleEvent.title));
                browser.addLocationListener(new LocationListener() {
                    @Override
                    public void changing(LocationEvent locationEvent) {
                        LOGGER.debug("Navigate to " + locationEvent.toString());
                        String location = locationEvent.location;

                        if (location.startsWith(redirectUri)) {

                            LOGGER.debug("Location starts with redirectUri, check code");
                            authenticator.handleCode(location);

                            shell.close();
                            dispose();
                        }

                    }

                    @Override
                    public void changed(LocationEvent locationEvent) {
                        LOGGER.debug("Page changed: " + locationEvent.toString());
                    }
                });

                shell.open();
                shell.setActive();
            } catch (SWTError e) {
                authenticator.errorCode = "SWT Error "+e.getMessage();
            }

        });
    }

    private void dispose() {
        Display.getDefault().asyncExec(() -> {
            if (browser != null) {
                browser.dispose();
            }
            if (shell != null) {
                shell.dispose();
            }
        });
    }
}
