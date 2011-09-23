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

/**
 * Folder Id.
 */
public class FolderId extends Option {
    protected String changeKey;
    protected String mailbox;

    /**
     * Create FolderId with specified tag name.
     *
     * @param name      field tag name
     * @param value     id value
     * @param changeKey folder change key
     * @param mailbox   shared mailbox name
     */
    protected FolderId(String name, String value, String changeKey, String mailbox) {
        this(name, value, changeKey);
        this.mailbox = mailbox;
    }

    /**
     * Create FolderId with specified tag name.
     *
     * @param name      field tag name
     * @param value     id value
     * @param changeKey folder change key
     */
    protected FolderId(String name, String value, String changeKey) {
        super(name, value);
        this.changeKey = changeKey;
    }

    /**
     * Build Folder id from response item.
     *
     * @param item    response item
     */
    public FolderId(EWSMethod.Item item) {
        this("t:FolderId", item.get("FolderId"), item.get("ChangeKey"));
    }


    /**
     * @inheritDoc
     */
    @Override
    public void write(Writer writer) throws IOException {
        writer.write('<');
        writer.write(name);
        writer.write(" Id=\"");
        writer.write(value);
        if (changeKey != null) {
            writer.write("\" ChangeKey=\"");
            writer.write(changeKey);
        }
        if (mailbox == null) {
            writer.write("\"/>");
        } else {
            writer.write("\"><t:Mailbox><t:EmailAddress>");
            writer.write(mailbox);
            writer.write("</t:EmailAddress></t:Mailbox></");
            writer.write(name);
            writer.write('>');
        }
    }

}
