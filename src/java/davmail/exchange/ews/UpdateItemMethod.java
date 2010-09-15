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

import java.util.List;

/**
 * Update Item method.
 */
public class UpdateItemMethod extends EWSMethod {
    /**
     * Update exchange item.
     *
     * @param messageDisposition save or send option
     * @param conflictResolution overwrite option
     * @param sendMeetingInvitationsOrCancellations
     *                           send invitations option
     * @param itemId             item id with change key
     * @param updates            field updates
     */
    public UpdateItemMethod(MessageDisposition messageDisposition, ConflictResolution conflictResolution,
                            SendMeetingInvitationsOrCancellations sendMeetingInvitationsOrCancellations,
                            ItemId itemId, List<FieldUpdate> updates) {
        super("Item", "UpdateItem");
        this.itemId = itemId;
        this.updates = updates;
        addMethodOption(messageDisposition);
        addMethodOption(conflictResolution);
        addMethodOption(sendMeetingInvitationsOrCancellations);
    }
}