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

import java.util.ArrayList;
import java.util.List;

/**
 * VCard property
 */
public class VProperty {
    protected static enum State {
        KEY, PARAM_NAME, PARAM_VALUE, QUOTED_PARAM_VALUE, VALUE, BACKSLASH
    }

    protected static class Param {
        String name;
        List<String> values;

        public void addAll(List<String> paramValues) {
            if (values == null) {
                values = new ArrayList<String>();
            }
            values.addAll(paramValues);
        }

        public String getValue() {
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            } else {
                return null;
            }
        }
    }

    protected String key;
    protected List<Param> params;
    protected List<String> values;

    /**
     * Create VProperty for key and value.
     *
     * @param name  property name
     * @param value property value
     */
    public VProperty(String name, String value) {
        setKey(name);
        setValue(value);
    }

    /**
     * Create VProperty from line.
     *
     * @param line card line
     */
    public VProperty(String line) {
        if (line != null && !"END:VCARD".equals(line)) {
            State state = State.KEY;
            String paramName = null;
            List<String> paramValues = null;
            int startIndex = 0;
            for (int i = 0; i < line.length(); i++) {
                char currentChar = line.charAt(i);
                if (state == State.KEY) {
                    if (currentChar == ':') {
                        setKey(line.substring(startIndex, i));
                        state = State.VALUE;
                        startIndex = i + 1;
                    } else if (currentChar == ';') {
                        setKey(line.substring(startIndex, i));
                        state = State.PARAM_NAME;
                        startIndex = i + 1;
                    }
                } else if (state == State.PARAM_NAME) {
                    if (currentChar == '=') {
                        paramName = line.substring(startIndex, i).toUpperCase();
                        state = State.PARAM_VALUE;
                        paramValues = new ArrayList<String>();
                        startIndex = i + 1;
                    } else if (currentChar == ';') {
                        // param with no value
                        paramName = line.substring(startIndex, i).toUpperCase();
                        addParam(paramName);
                        state = State.PARAM_NAME;
                        startIndex = i + 1;
                    } else if (currentChar == ':') {
                        // param with no value
                        paramName = line.substring(startIndex, i).toUpperCase();
                        addParam(paramName);
                        state = State.VALUE;
                        startIndex = i + 1;
                    }
                } else if (state == State.PARAM_VALUE) {
                    if (currentChar == '"') {
                        state = State.QUOTED_PARAM_VALUE;
                        startIndex = i + 1;
                    } else if (currentChar == ':') {
                        if (startIndex < i) {
                            paramValues.add(line.substring(startIndex, i));
                        }
                        addParam(paramName, paramValues);
                        state = State.VALUE;
                        startIndex = i + 1;
                    } else if (currentChar == ';') {
                        if (startIndex < i) {
                            paramValues.add(line.substring(startIndex, i));
                        }
                        addParam(paramName, paramValues);
                        state = State.PARAM_NAME;
                        startIndex = i + 1;
                    } else if (currentChar == ',') {
                        if (startIndex < i) {
                            paramValues.add(line.substring(startIndex, i));
                        }
                        startIndex = i + 1;
                    }
                } else if (state == State.QUOTED_PARAM_VALUE) {
                    if (currentChar == '"') {
                        state = State.PARAM_VALUE;
                        paramValues.add(line.substring(startIndex, i));
                        startIndex = i + 1;
                    }
                } else if (state == State.VALUE) {
                    if (currentChar == '\\') {
                        state = State.BACKSLASH;
                    } else if (currentChar == ';') {
                        addValue(line.substring(startIndex, i));
                        startIndex = i + 1;
                    }
                } else if (state == State.BACKSLASH) {
                    state = State.VALUE;
                }
            }
            addValue(line.substring(startIndex));
        }
    }

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
        if (values == null || values.isEmpty()) {
            return null;
        } else {
            return values.get(0);
        }
    }

    /**
     * Property values.
     *
     * @return values
     */
    public List<String> getValues() {
        return values;
    }

    /**
     * Test if the property has a param named paramName with given value.
     *
     * @param paramName  param name
     * @param paramValue param value
     * @return true if property has param name and value
     */
    public boolean hasParam(String paramName, String paramValue) {
        return params != null && getParam(paramName) != null && containsIgnoreCase(getParam(paramName).values, paramValue);
    }

    /**
     * Test if the property has a param named paramName.
     *
     * @param paramName param name
     * @return true if property has param name
     */
    public boolean hasParam(String paramName) {
        return params != null && getParam(paramName) != null;
    }

    /**
     * Remove param from property.
     *
     * @param paramName param name
     */
    public void removeParam(String paramName) {
        if (params != null) {
            Param param = getParam(paramName);
            if (param != null) {
                params.remove(param);
            }
        }
    }

    protected boolean containsIgnoreCase(List<String> stringCollection, String value) {
        for (String collectionValue : stringCollection) {
            if (value.equalsIgnoreCase(collectionValue)) {
                return true;
            }
        }
        return false;
    }

    protected void addParam(String paramName) {
        addParam(paramName, (String) null);
    }

    public void addParam(String paramName, String paramValue) {
        List<String> paramValues = new ArrayList<String>();
        paramValues.add(paramValue);
        addParam(paramName, paramValues);
    }

    protected void addParam(String paramName, List<String> paramValues) {
        if (params == null) {
            params = new ArrayList<Param>();
        }
        Param currentParam = getParam(paramName);
        if (currentParam == null) {
            currentParam = new Param();
            currentParam.name = paramName;
            params.add(currentParam);
        }
        currentParam.addAll(paramValues);
    }

    protected Param getParam(String paramName) {
        if (params != null) {
            for (Param param : params) {
                if (paramName.equals(param.name)) {
                    return param;
                }
            }
        }
        return null;
    }

    protected List<Param> getParams() {
        return params;
    }

    protected void setParams(List<Param> params) {
        this.params = params;
    }

    protected void setValue(String value) {
        if (value == null) {
            values = null;
        } else {
            if (values == null) {
                values = new ArrayList<String>();
            } else {
                values.clear();
            }
            values.add(decodeValue(value));
        }
    }

    protected void addValue(String value) {
        if (values == null) {
            values = new ArrayList<String>();
        }
        values.add(decodeValue(value));
    }

    protected String decodeValue(String value) {
        if (value == null || (value.indexOf('\\') < 0 && value.indexOf(',') < 0)) {
            return value;
        } else {
            // decode value
            StringBuilder decodedValue = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\\') {
                    //noinspection AssignmentToForLoopParameter
                    i++;
                    if (i == value.length()) {
                        break;
                    }
                    c = value.charAt(i);
                    if (c == 'n' || c == 'N') {
                        c = '\n';
                    } else if (c == 'r') {
                        c = '\r';
                    }
                }
                // iPhone encodes category separator
                if (c == ',' &&
                        // multivalued properties
                        ("N".equals(key) ||
                                "ADR".equals(key) ||
                                "CATEGORIES".equals(key) ||
                                "NICKNAME".equals(key)
                        )) {
                    // convert multiple values to multiline values (e.g. street)
                    c = '\n';
                }
                decodedValue.append(c);
            }
            return decodedValue.toString();
        }
    }

    /**
     * Set property key.
     *
     * @param key property key
     */
    public void setKey(String key) {
        int dotIndex = key.indexOf('.');
        if (dotIndex < 0) {
            this.key = key;
        } else {
            this.key = key.substring(dotIndex + 1);
        }
    }

    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(key);
        if (params != null) {
            for (Param param : params) {
                buffer.append(';').append(param.name);
                if (param.values != null) {
                    buffer.append('=');
                    for (String value : param.values) {
                        appendParamValue(buffer, value);
                    }
                }
            }
        }
        buffer.append(':');
        if (values != null) {
            boolean firstValue = true;
            for (String value : values) {
                if (firstValue) {
                    firstValue = false;
                } else {
                    buffer.append(';');
                }
                appendMultilineEncodedValue(buffer, value);
            }
        }
        return buffer.toString();
    }

    protected void appendParamValue(StringBuilder buffer, String value) {
        if (value.indexOf(';') >= 0 || value.indexOf(',') >= 0
                || value.indexOf('(') >= 0 || value.indexOf('/') >= 0
                || value.indexOf(':') >= 0) {
            buffer.append('"').append(value).append('"');
        } else {
            buffer.append(value);
        }
    }

    /**
     * Append and encode \n to \\n in value.
     *
     * @param buffer line buffer
     * @param value  value
     */
    protected void appendMultilineEncodedValue(StringBuilder buffer, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n') {
                buffer.append("\\n");
            } else {
                buffer.append(value.charAt(i));
            }
        }
    }

}
