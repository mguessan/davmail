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
package davmail.exchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for VCalendar, VTimezone, VEvent.
 */
public class VObject {
    /**
     * VObject properties
     */
    ArrayList<VProperty> properties;
    /**
     * Inner VObjects (e.g. VEVENT, VALARM, ...)
     */
    ArrayList<VObject> vObjects;
    /**
     * Object base name (VCALENDAR, VEVENT, VCARD...).
     */
    public String type;

    /**
     * Create VObject with given type
     *
     * @param beginProperty first line property
     * @param reader        stream reader just after the BEGIN:TYPE line
     * @throws IOException on error
     */
    public VObject(VProperty beginProperty, BufferedReader reader) throws IOException {
        if (!"BEGIN".equals(beginProperty.getKey())) {
            throw new IOException("Invalid first line: " + beginProperty);
        }
        type = beginProperty.getValue();
        String endLine = "END:" + type;
        String line = reader.readLine();
        while (line != null && !endLine.equals(line)) {
            handleLine(line, reader);
            line = reader.readLine();
        }
        if (line == null) {
            throw new IOException("Unexpected end of stream");
        }
    }

    /**
     * Create VObject from reader.
     *
     * @param reader stream reader just after the BEGIN:TYPE line
     * @throws IOException on error
     */
    public VObject(BufferedReader reader) throws IOException {
        this(new VProperty(reader.readLine()), reader);
    }

    /**
     * Create VCalendar object from string;
     *
     * @param itemBody item body
     * @throws IOException on error
     */
    public VObject(String itemBody) throws IOException {
        this(new ICSBufferedReader(new StringReader(itemBody)));
    }


    protected void handleLine(String line, BufferedReader reader) throws IOException {
        VProperty property = new VProperty(line);
        // inner object
        if ("BEGIN".equals(property.getKey())) {
            addVObject(new VObject(property, reader));
        } else {
            addProperty(property);
        }
    }

    protected void addVObject(VObject vObject) {
        if (vObjects == null) {
            vObjects = new ArrayList<VObject>();
        }
        vObjects.add(vObject);
    }

    protected void addProperty(VProperty property) {
        if (property.getValue() != null) {
            if (properties == null) {
                properties = new ArrayList<VProperty>();
            }
            properties.add(property);
        }
    }

    /**
     * Write VObject to writer.
     *
     * @param writer buffered writer
     */
    public void writeTo(ICSBufferedWriter writer) {
        writer.write("BEGIN:");
        writer.writeLine(type);
        if (properties != null) {
            for (VProperty property : properties) {
                writer.writeLine(property.toString());
            }
        }
        if (vObjects != null) {
            for (VObject object : vObjects) {
                object.writeTo(writer);
            }
        }
        writer.write("END:");
        writer.writeLine(type);
    }

    public String toString() {
        ICSBufferedWriter writer = new ICSBufferedWriter();
        writeTo(writer);
        return writer.toString();
    }

    /**
     * Get VObject properties
     *
     * @return properties
     */
    public List<VProperty> getProperties() {
        return properties;
    }

    public VProperty getProperty(String name) {
        if (properties != null) {
            for (VProperty property : properties) {
                if (property.getKey().equalsIgnoreCase(name)) {
                    return property;
                }
            }

        }
        return null;
    }

    public String getPropertyValue(String name) {
        VProperty property = getProperty(name);
        if (property != null) {
            return property.getValue();
        } else {
            return null;
        }
    }

    public void setPropertyValue(String name, String value) {
        if (value == null) {
            removeProperty(name);
        } else {
            VProperty property = getProperty(name);
            if (property == null) {
                property = new VProperty(name, value);
                addProperty(property);
            } else {
                property.setValue(value);
            }
        }
    }

    public void removeProperty(String name) {
        if (vObjects != null) {
            VProperty property = getProperty(name);
            if (property != null) {
                vObjects.remove(property);
            }
        }
    }
}
