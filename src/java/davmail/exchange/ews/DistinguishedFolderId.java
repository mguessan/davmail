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

import java.util.HashMap;
import java.util.Map;

/**
 * Distinguished Folder Id.
 */
public final class DistinguishedFolderId extends FolderId {

    private DistinguishedFolderId(String value) {
        super("t:DistinguishedFolderId", value, null);
    }

    private DistinguishedFolderId(String value, String mailbox) {
        super("t:DistinguishedFolderId", value, null, mailbox);
    }

    /**
     * DistinguishedFolderId names
     */
    @SuppressWarnings({"UnusedDeclaration", "JavaDoc"})
    public static enum Name {
        calendar, contacts, deleteditems, drafts, inbox, journal, notes, outbox, sentitems, tasks, msgfolderroot,
        publicfoldersroot, root, junkemail, searchfolders, voicemail,
        archiveroot
    }

    private static final Map<Name, DistinguishedFolderId> folderIdMap = new HashMap<Name, DistinguishedFolderId>();

    static {
        for (Name name : Name.values()) {
            folderIdMap.put(name, new DistinguishedFolderId(name.toString()));
        }
    }

    /**
     * Get DistinguishedFolderId object for mailbox and name.
     *
     * @param mailbox mailbox name
     * @param name    folder id name
     * @return DistinguishedFolderId object
     */
    public static DistinguishedFolderId getInstance(String mailbox, Name name) {
        if (mailbox == null) {
            return folderIdMap.get(name);
        } else {
            return new DistinguishedFolderId(name.toString(), mailbox);
        }
    }

}