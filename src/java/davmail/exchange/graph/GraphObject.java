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

package davmail.exchange.graph;

import davmail.exception.DavMailException;
import davmail.exchange.ews.Field;
import davmail.exchange.ews.FieldURI;
import davmail.util.StringUtil;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.TimeZone;

/**
 * Wrapper for Graph API JsonObject
 */
public class GraphObject {
    protected final JSONObject jsonObject;
    protected int statusCode;

    public GraphObject(JSONObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public String optString(String key) {
        String value = jsonObject.optString(key, null);
        // special case for keywords/categories
        if ("keywords".equals(key) || "categories".equals(key)) {
            JSONArray categoriesArray = jsonObject.optJSONArray("categories");
            HashSet<String> keywords = new HashSet<>();
            for (int j = 0; j < categoriesArray.length(); j++) {
                keywords.add(categoriesArray.optString(j));
            }
            value = StringUtil.join(keywords, ",");
        }
        // try to fetch from expanded properties
        else if (value == null) {
            key = Field.get(key).getGraphId();
            // remapped attributes first
            value = jsonObject.optString(key, null);
            // check expanded properties
            if (value == null) {
                JSONArray singleValueExtendedProperties = jsonObject.optJSONArray("singleValueExtendedProperties");
                if (singleValueExtendedProperties != null) {
                    for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                        JSONObject singleValueObject = singleValueExtendedProperties.optJSONObject(i);
                        if (singleValueObject != null && key.equals(singleValueObject.optString("id"))) {
                            value = singleValueObject.optString("value");
                        }

                    }
                }
            }
        }
        return value;
    }

    public JSONArray optJSONArray(String key) {
        return jsonObject.optJSONArray(key);
    }

    public void put(String alias, String value) throws JSONException {
        FieldURI field = Field.get(alias);
        String key = field.getGraphId();
        // assume all expanded properties have a space
        if (key.contains(" ")) {
            if (field.isNumber() && value == null) {
                value = "0";
            }
            getSingleValueExtendedProperties().put(new JSONObject().put("id", key).put("value", value == null?JSONObject.NULL:value));
        } else {
            jsonObject.put(key, value == null?JSONObject.NULL:value);
        }
    }

    public void put(String alias, boolean value) throws JSONException {
        FieldURI field = Field.get(alias);
        String key = field.getGraphId();
        // assume all expanded properties have a space
        if (key.contains(" ")) {
            getSingleValueExtendedProperties().put(new JSONObject().put("id", key).put("value", value));
        } else {
            jsonObject.put(key, value);
        }
    }

    public void put(String key, JSONArray values) throws JSONException {
        jsonObject.put(key, values);
    }

    public void setCategories(String values) throws JSONException {
        if (values != null) {
            setCategories(values.split(","));
        } else {
            jsonObject.put("categories", new JSONArray());
        }
    }

    public void setCategories(String[] values) throws JSONException {
        // assume all expanded properties have a space
        JSONArray jsonValues = new JSONArray();
        for (String singleValue : values) {
            jsonValues.put(singleValue);
        }
        jsonObject.put("categories", jsonValues);
    }

    public String toString(int indentFactor) throws JSONException {
        return jsonObject.toString(indentFactor);
    }

    protected JSONArray getSingleValueExtendedProperties() throws JSONException {
        JSONArray singleValueExtendedProperties = jsonObject.optJSONArray("singleValueExtendedProperties");
        if (singleValueExtendedProperties == null) {
            singleValueExtendedProperties = new JSONArray();
            jsonObject.put("singleValueExtendedProperties", singleValueExtendedProperties);
        }
        return singleValueExtendedProperties;
    }


    public String getString(String key) throws JSONException {
        String value = optString(key);
        if (value == null) {
            throw new JSONException("JSONObject[" + key + "] not found.");
        }
        return value;
    }

    public String optString(String section, String key) {
        JSONObject sectionObject = jsonObject.optJSONObject(section);
        // try to map with field
        if (sectionObject == null) {
            sectionObject = jsonObject.optJSONObject(Field.get(section).getGraphId());
        }
        if (sectionObject != null) {
            return sectionObject.optString(key, null);
        }
        return null;
    }

    public JSONObject optJSONObject(String key) {
        return jsonObject.optJSONObject(key);
    }

    public Date optDateTimeTimeZone(String key) throws DavMailException {
        JSONObject sectionObject = jsonObject.optJSONObject(key);
        if (sectionObject != null) {
            String timeZone = sectionObject.optString("timeZone");
            String dateTime = sectionObject.optString("dateTime");
            if (timeZone != null && dateTime != null) {
                try {
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS");
                    parser.setTimeZone(TimeZone.getTimeZone(timeZone));
                    return parser.parse(dateTime);
                } catch (ParseException e) {
                    throw new DavMailException("EXCEPTION_INVALID_DATE", dateTime);
                }
            }
        }
        return null;
    }
}

