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
package davmail.http;

import davmail.BundleMessage;
import davmail.ui.PasswordPromptDialog;

/**
 * Ask user one time password.
 */
public final class DavGatewayOTPPrompt {
    private DavGatewayOTPPrompt() {
    }

    /**
     * Ask user token password
     *
     * @return user provided one time password
     */
    public static String getOneTimePassword() {
        PasswordPromptDialog passwordPromptDialog = new PasswordPromptDialog(BundleMessage.format("UI_OTP_PASSWORD_PROMPT"));
        return String.valueOf(passwordPromptDialog.getPassword());
    }
}
