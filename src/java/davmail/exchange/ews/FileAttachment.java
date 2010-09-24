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
 * File Attachment.
 */
public class FileAttachment {
    protected String name;
    protected String contentType;
    protected String content;
    protected String attachmentId;
    protected boolean isContactPhoto;

    /**
     * Default constructor
     */
    public FileAttachment() {
        // empty constructor
    }

    /**
     * Build file attachment.
     *
     * @param name        attachment name
     * @param contentType content type
     * @param content     body as string
     */
    public FileAttachment(String name, String contentType, String content) {
        this.name = name;
        this.contentType = contentType;
        this.content = content;
    }

    /**
     * Write XML content to writer.
     *
     * @param writer writer
     * @throws IOException on error
     */
    public void write(Writer writer) throws IOException {
        writer.write("<t:FileAttachment>");
        if (name != null) {
            writer.write("<t:Name>");
            writer.write(name);
            writer.write("</t:Name>");
        }
        if (contentType != null) {
            writer.write("<t:ContentType>");
            writer.write(contentType);
            writer.write("</t:ContentType>");
        }
        if (isContactPhoto) {
            writer.write("<t:IsContactPhoto>true</t:IsContactPhoto>");
        }
        if (content != null) {
            writer.write("<t:Content>");
            writer.write(content);
            writer.write("</t:Content>");
        }
        writer.write("</t:FileAttachment>");
    }

    /**
     * Exchange 2010 only: set contact photo flag on attachment.
     *
     * @param isContactPhoto contact photo flag
     */
    public void setIsContactPhoto(boolean isContactPhoto) {
        this.isContactPhoto = isContactPhoto;
    }

}
