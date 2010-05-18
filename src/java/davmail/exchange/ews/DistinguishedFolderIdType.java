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

import java.io.IOException;
import java.io.Writer;

public class DistinguishedFolderIdType extends FolderIdType {

    private DistinguishedFolderIdType(String value) {
        super(value);
    }

    @Override
    public void write(Writer writer) throws IOException {
        writer.write("<t:DistinguishedFolderId Id=\"");
        writer.write(value);
        writer.write("\"/>");
    }

    public static final DistinguishedFolderIdType calendar = new DistinguishedFolderIdType("calendar");
    public static final DistinguishedFolderIdType contacts = new DistinguishedFolderIdType("contacts");
    public static final DistinguishedFolderIdType deleteditems = new DistinguishedFolderIdType("deleteditems");
    public static final DistinguishedFolderIdType drafts = new DistinguishedFolderIdType("drafts");
    public static final DistinguishedFolderIdType inbox = new DistinguishedFolderIdType("inbox");
    public static final DistinguishedFolderIdType journal = new DistinguishedFolderIdType("journal");
    public static final DistinguishedFolderIdType notes = new DistinguishedFolderIdType("notes");
    public static final DistinguishedFolderIdType outbox = new DistinguishedFolderIdType("outbox");
    public static final DistinguishedFolderIdType sentitems = new DistinguishedFolderIdType("sentitems");
    public static final DistinguishedFolderIdType tasks = new DistinguishedFolderIdType("tasks");
    public static final DistinguishedFolderIdType msgfolderroot = new DistinguishedFolderIdType("msgfolderroot");
    public static final DistinguishedFolderIdType publicfoldersroot = new DistinguishedFolderIdType("publicfoldersroot");
    public static final DistinguishedFolderIdType root = new DistinguishedFolderIdType("root");
    public static final DistinguishedFolderIdType junkemail = new DistinguishedFolderIdType("junkemail");
    public static final DistinguishedFolderIdType searchfolders = new DistinguishedFolderIdType("searchfolders");
    public static final DistinguishedFolderIdType voicemail = new DistinguishedFolderIdType("voicemail");

}