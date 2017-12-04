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

import davmail.exchange.XMLStreamUtil;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.Writer;

public class GetUserPhotoMethod extends EWSMethod  {
    public enum SizeRequested {
        HR48x48, HR64x64, HR96x96, HR120x120, HR240x240, HR360x360,
        HR432x432, HR504x504, HR648x648
    }

    protected String email;
    protected SizeRequested sizeRequested;

    protected String contentType = null;
    protected String pictureData = null;

    /**
     * Get User Configuration method.
     */
    public GetUserPhotoMethod(String email, SizeRequested sizeRequested) {
        super("GetUserPhoto", "GetUserPhoto");
        this.email = email;
        this.sizeRequested = sizeRequested;
    }

    @Override
    protected void writeSoapBody(Writer writer) throws IOException {
        writer.write("<m:Email>");
        writer.write(email);
        writer.write("</m:Email>");

        writer.write("<m:SizeRequested>");
        writer.write(sizeRequested.toString());
        writer.write("</m:SizeRequested>");

    }

    protected Item createResponseItem() {
        if (responseItems.isEmpty()) {
            Item responseItem = new Item();
            responseItems.add(responseItem);
            return responseItem;
        } else {
            return responseItems.get(0);
        }
    }

    @Override
    protected void handleCustom(XMLStreamReader reader) throws XMLStreamException {
        if (XMLStreamUtil.isStartTag(reader, "PictureData")) {
            pictureData = reader.getElementText();
        }
        if (XMLStreamUtil.isStartTag(reader, "ContentType")) {
            contentType = reader.getElementText();
        }

    }

    public String getContentType() {
        return contentType;
    }

    public String getPictureData() {
        return pictureData;
    }
}
