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
 * Distinguished Folder Id.
 */
public class DistinguishedFolderId extends FolderId {

    private DistinguishedFolderId(String value) {
        super("t:DistinguishedFolderId", value);
    }

    public static final DistinguishedFolderId CALENDAR = new DistinguishedFolderId("calendar");
    public static final DistinguishedFolderId CONTACTS = new DistinguishedFolderId("contacts");
    public static final DistinguishedFolderId DELETEDITEMS = new DistinguishedFolderId("deleteditems");
    public static final DistinguishedFolderId DRAFTS = new DistinguishedFolderId("drafts");
    public static final DistinguishedFolderId INBOX = new DistinguishedFolderId("inbox");
    public static final DistinguishedFolderId JOURNAL = new DistinguishedFolderId("journal");
    public static final DistinguishedFolderId NOTES = new DistinguishedFolderId("notes");
    public static final DistinguishedFolderId OUTBOX = new DistinguishedFolderId("outbox");
    public static final DistinguishedFolderId SENTITEMS = new DistinguishedFolderId("sentitems");
    public static final DistinguishedFolderId TASKS = new DistinguishedFolderId("tasks");
    public static final DistinguishedFolderId MSGFOLDERROOT = new DistinguishedFolderId("msgfolderroot");
    public static final DistinguishedFolderId PUBLICFOLDERSROOT = new DistinguishedFolderId("publicfoldersroot");
    public static final DistinguishedFolderId ROOT = new DistinguishedFolderId("root");
    public static final DistinguishedFolderId JUNKEMAIL = new DistinguishedFolderId("junkemail");
    public static final DistinguishedFolderId SEARCHFOLDERS = new DistinguishedFolderId("searchfolders");
    public static final DistinguishedFolderId VOICEMAIL = new DistinguishedFolderId("voicemail");

}