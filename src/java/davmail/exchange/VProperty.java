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

import java.util.*;

/**
 * VCard property
 */
public class VProperty {

    protected enum State {
        KEY, PARAM_NAME, PARAM_VALUE, QUOTED_PARAM_VALUE, QUOTED_PARAM_VALUE_BACKSLASH, VALUE, BACKSLASH
    }

    protected static final HashSet<String> MULTIVALUED_PROPERTIES = new HashSet<>();

    static {
        MULTIVALUED_PROPERTIES.add("RESOURCES");
        MULTIVALUED_PROPERTIES.add("LOCATION");
    }

    protected static class Param {
        String name;
        List<String> values;

        public void addAll(List<String> paramValues) {
            if (values == null) {
                values = new ArrayList<>();
            }
            values.addAll(paramValues);
        }

        protected String getValue() {
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
                        paramValues = new ArrayList<>();
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
                            paramValues = addParamValue(paramValues, line.substring(startIndex, i));
                        }
                        addParam(paramName, paramValues);
                        state = State.VALUE;
                        startIndex = i + 1;
                    } else if (currentChar == ';') {
                        if (startIndex < i) {
                            paramValues = addParamValue(paramValues, line.substring(startIndex, i));
                        }
                        addParam(paramName, paramValues);
                        state = State.PARAM_NAME;
                        startIndex = i + 1;
                    } else if (currentChar == ',') {
                        if (startIndex < i) {
                            paramValues = addParamValue(paramValues, line.substring(startIndex, i));
                        }
                        startIndex = i + 1;
                    }
                } else if (state == State.QUOTED_PARAM_VALUE) {
                    if (currentChar == '\\') {
                        state = State.QUOTED_PARAM_VALUE_BACKSLASH;
                    } else if (currentChar == '"') {
                        state = State.PARAM_VALUE;
                        paramValues = addParamValue(paramValues, line.substring(startIndex, i));
                        startIndex = i + 1;
                    }
                } else if (state == State.QUOTED_PARAM_VALUE_BACKSLASH){
                    state = State.QUOTED_PARAM_VALUE;
                } else if (state == State.VALUE) {
                    if (currentChar == '\\') {
                        state = State.BACKSLASH;
                    } else if (currentChar == ';' || (MULTIVALUED_PROPERTIES.contains(key) && currentChar == ',')) {
                        addValue(line.substring(startIndex, i));
                        startIndex = i + 1;
                    }
                } else if (state == State.BACKSLASH) {
                    state = State.VALUE;
                }
            }
            if (state == State.VALUE) {
                addValue(line.substring(startIndex));
            } else {
                throw new IllegalArgumentException("Invalid property line: " + line);
            }
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
     * Return property values as a map.
     * Typical use for RRULE content
     * @return values as map
     */
    public Map<String, String> getValuesAsMap() {
        HashMap<String, String> valuesMap = new HashMap<>();

        if (values != null) {
            for (String value:values) {
                if (value.contains("=")) {
                    int index = value.indexOf("=");
                    valuesMap.put(value.substring(0, index), value.substring(index+1));
                }
            }
        }
        return valuesMap;
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

    /**
     * Add value to paramValues and return list, create list if null.
     *
     * @param paramValues value list
     * @param value new value
     * @return updated value list
     */
    protected List<String> addParamValue(List<String> paramValues, String value) {
        List<String> result = paramValues;
        if (result == null) {
            result = new ArrayList<>();
        }
        result.add(value);
        return result;
    }

    protected void addParam(String paramName) {
        addParam(paramName, (String) null);
    }

    /**
     * Set param value on property.
     *
     * @param paramName  param name
     * @param paramValue param value
     */
    public void setParam(String paramName, String paramValue) {
        Param currentParam = getParam(paramName);
        if (currentParam != null) {
            params.remove(currentParam);
        }
        addParam(paramName, paramValue);
    }

    /**
     * Add param value on property.
     *
     * @param paramName  param name
     * @param paramValue param value
     */
    public void addParam(String paramName, String paramValue) {
        if (paramValue != null) {
            List<String> paramValues = new ArrayList<>();
            paramValues.add(paramValue);
            addParam(paramName, paramValues);
        }
    }

    protected void addParam(String paramName, List<String> paramValues) {
        if (params == null) {
            params = new ArrayList<>();
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
        if (params != null && paramName != null) {
            for (Param param : params) {
                if (paramName.equals(param.name)) {
                    return param;
                }
            }
        }
        return null;
    }

    /**
     * Return param value.
     * @param paramName param name
     * @return value
     */
    public String getParamValue(String paramName) {
        Param param = getParam(paramName);
        if (param != null) {
            return param.getValue();
        } else {
            return null;
        }
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
                values = new ArrayList<>();
            } else {
                values.clear();
            }
            values.add(decodeValue(value));
        }
    }

    protected void addValue(String value) {
        if (values == null) {
            values = new ArrayList<>();
        }
        values.add(decodeValue(value));
    }

    public void removeValue(String value) {
        if (values != null) {
            int index = -1;
            for (int i=0;i<values.size();i++) {
                if (value.equals(values.get(i))) {
                    index = i;
                }
            }
            if (index >= 0) {
                values.remove(index);
            }
        }
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
                                //"CATEGORIES".equals(key) ||
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
                appendParamValues(buffer, param);
            }
        }
        buffer.append(':');
        if (values != null) {
            boolean firstValue = true;
            for (String value : values) {
                if (firstValue) {
                    firstValue = false;
                } else if (MULTIVALUED_PROPERTIES.contains(key)) {
                    buffer.append(',');
                } else {
                    buffer.append(';');
                }
                appendMultilineEncodedValue(buffer, value);
            }
        }
        return buffer.toString();
    }

    protected void appendParamValues(StringBuilder buffer, Param param) {
        if (param.values != null) {
            buffer.append('=');
            boolean first = true;
            for (String value : param.values) {
                if (first) {
                    first = false;
                } else {
                    buffer.append(',');
                }
                // always quote CN param
                if ("CN".equalsIgnoreCase(param.name)
                        // quote param values with special characters
                        || value.indexOf(';') >= 0 || value.indexOf(',') >= 0
                        || value.indexOf('(') >= 0 || value.indexOf('/') >= 0
                        || value.indexOf(':') >= 0) {
                    buffer.append('"').append(value).append('"');
                } else {
                    buffer.append(value);
                }
            }
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
            } else if (MULTIVALUED_PROPERTIES.contains(key) && c == ',') {
                buffer.append('\\').append(',');
            // skip carriage return
            } else if (c != '\r') {
                buffer.append(value.charAt(i));
            }
        }
    }

}

