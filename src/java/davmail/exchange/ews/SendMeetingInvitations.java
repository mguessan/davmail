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
package davmail.exchange.ews;

/**
 * Item update option.
 */
@SuppressWarnings({"UnusedDeclaration", "JavaDoc"})
public final class SendMeetingInvitations extends AttributeOption {
    private SendMeetingInvitations(String value) {
        super("SendMeetingInvitations", value);
    }

    public static final SendMeetingInvitations SendToNone = new SendMeetingInvitations("SendToNone");
    public static final SendMeetingInvitations SendOnlyToAll = new SendMeetingInvitations("SendOnlyToAll");
    public static final SendMeetingInvitations SendToAllAndSaveCopy = new SendMeetingInvitations("SendToAllAndSaveCopy");
}
