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

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * VCARD reader.
 */
public class VCardReader extends ICSBufferedReader {
    /**
     * VCard property
     */
    public class Property {
        protected String key;
        protected Map<String, Set<String>> params;
        protected String value;

        /**
         * Property key, without optional parameters (e.g. TEL).
         *
         * @return key
         */
        public String getKey() {
            return key;
        }

        /**
         * Property value.
         *
         * @return value
         */
        public String getValue() {
            return value;
        }

        /**
         * Property values, split on ;.
         *
         * @return values
         */
        public String[] getValues() {
            // TODO: handle protected characters
            return value.split(";");
        }

        public boolean hasParam(String paramName, String paramValue) {
            return params.containsKey(paramName) && params.get(paramName).contains(paramValue);
        }

        protected void addParam(String paramName, Set<String> paramValues) {
            if (params == null) {
                params = new HashMap<String, Set<String>>();
            }
            params.put(paramName, paramValues);
        }
    }

    /**
     * Create a VCARD reader on the provided reader
     *
     * @param in input reader
     * @throws IOException on error
     */
    public VCardReader(Reader in) throws IOException {
        super(in);
    }

    protected static enum State {
        KEY, PARAM_NAME, PARAM_VALUE, VALUE
    }

    public Property readProperty() throws IOException {
        Property property = null;
        String line = readLine();
        if (line != null && !"END:VCARD".equals(line)) {
            property = new Property();
            State state = State.KEY;
            String paramName = null;
            Set<String> paramValues = null;
            int startIndex = 0;
            for (int i = 0; i < line.length(); i++) {
                char currentChar = line.charAt(i);
                if (state == State.KEY) {
                    if (currentChar == ':') {
                        property.key = line.substring(startIndex, i);
                        state = State.VALUE;
                        startIndex = i + 1;
                    } else if (currentChar == ';') {
                        property.key = line.substring(startIndex, i);
                        state = State.PARAM_NAME;
                        startIndex = i + 1;
                    }
                } else if (state == State.PARAM_NAME) {
                    if (currentChar == '=') {
                        paramName = line.substring(startIndex, i);
                        state = State.PARAM_VALUE;
                        paramValues = new HashSet<String>();
                        startIndex = i + 1;
                    }
                } else if (state == State.PARAM_VALUE) {
                    if (currentChar == ':') {
                        paramValues.add(line.substring(startIndex, i));
                        property.addParam(paramName, paramValues);
                        state = State.VALUE;
                        startIndex = i + 1;
                    } else if (currentChar == ';') {
                        paramValues.add(line.substring(startIndex, i));
                        property.addParam(paramName, paramValues);
                        state = State.PARAM_NAME;
                        startIndex = i + 1;
                    } else if (currentChar == ',') {
                        paramValues.add(line.substring(startIndex, i));
                        startIndex = i + 1;
                    }
                }
            }
            property.value = line.substring(startIndex);
        }
        return property;
    }
}
