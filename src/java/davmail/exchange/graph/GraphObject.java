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
import davmail.exchange.VProperty;
import davmail.exchange.ews.Field;
import davmail.exchange.ews.FieldURI;
import davmail.util.DateUtil;
import davmail.util.StringUtil;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.TimeZone;

import static davmail.util.DateUtil.CALDAV_DATE_TIME;
import static davmail.util.DateUtil.GRAPH_DATE_TIME;

/**
 * Wrapper for Graph API JsonObject
 */
public class GraphObject {
    protected static final Logger LOGGER = Logger.getLogger(GraphObject.class);

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
        } else if ("changeKey".equals(key) && value == null) {
            // tasks don't have an etag field, use @odata.etag
            String odataEtag = optString("@odata.etag");
            if (odataEtag != null && odataEtag.startsWith("W/\"") && odataEtag.endsWith("\"")) {
                value = odataEtag.substring(3, odataEtag.length() - 1);
            }
        } else if (value == null) {
            // use field mapping to get value
            value = optFieldString(key);
        }
        return value;
    }

    public String optFieldString(String alias) {
        String key = Field.get(alias).getGraphId();
        // remapped attributes first
        String value = jsonObject.optString(key, null);
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
        return value;
    }

    public JSONArray optJSONArray(String key) {
        return jsonObject.optJSONArray(key);
    }

    /**
     * Set value on field.
     * First map property alias to a field to determine graph id, then set graph property or singleValueExtendedProperty
     * @param alias field alias
     * @param value property value
     * @throws JSONException on error
     */
    public void put(String alias, String value) throws JSONException {
        FieldURI field = Field.get(alias);
        String key = field.getGraphId();
        // assume all expanded properties have a space
        if (key.contains(" ")) {
            // TODO remove this
            if (field.isNumber() && value == null) {
                value = "0";
            }
            getSingleValueExtendedProperties().put(new JSONObject().put("id", key).put("value", value == null ? JSONObject.NULL : value));
        } else {
            jsonObject.put(key, value == null ? JSONObject.NULL : value);
        }
    }

    /**
     * Set boolean value on field.
     * First map property alias to a field to determine graph id, then set graph property or singleValueExtendedProperty
     * @param alias field alias
     * @param value property value
     * @throws JSONException on error
     */
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

    /**
     * Set categories from comma separated values.
     * @param values comma separated values
     * @throws JSONException on error
     */
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


    /**
     * Get singleValueExtendedProperties json array.
     * Create an empty json array on first call.
     * @return singleValueExtendedProperties array
     * @throws JSONException on error
     */
    protected JSONArray getSingleValueExtendedProperties() throws JSONException {
        JSONArray singleValueExtendedProperties = jsonObject.optJSONArray("singleValueExtendedProperties");
        if (singleValueExtendedProperties == null) {
            singleValueExtendedProperties = new JSONArray();
            jsonObject.put("singleValueExtendedProperties", singleValueExtendedProperties);
        }
        return singleValueExtendedProperties;
    }


    /**
     * Get mandatory property value.
     * @param key property name
     * @return value
     * @throws JSONException on missing property
     */
    public String getString(String key) throws JSONException {
        String value = optString(key);
        if (value == null) {
            throw new JSONException("JSONObject[" + key + "] not found.");
        }
        return value;
    }

    /**
     * Get optional parameter from json property.
     * @param section json property name
     * @param key internal property name
     * @return value or null
     */
    public String optString(String section, String key) {
        JSONObject sectionObject = jsonObject.optJSONObject(section);
        if (sectionObject != null) {
            return sectionObject.optString(key, null);
        }
        return null;
    }

    public JSONObject optJSONObject(String key) {
        return jsonObject.optJSONObject(key);
    }

    /**
     * Convert datetimetimezone to java date.
     * Graph return some dates as json object with dateTime and timeZone, see
     * <a href="https://learn.microsoft.com/en-us/graph/api/resources/datetimetimezone">datetimetimezone</a>
     * @param key property key, e.g. dueDateTime
     * @return java date
     * @throws DavMailException on error
     */
    public Date optDateTimeTimeZone(String key) throws DavMailException {
        JSONObject sectionObject = jsonObject.optJSONObject(key);
        if (sectionObject != null) {
            String timeZone = sectionObject.optString("timeZone");
            String dateTime = sectionObject.optString("dateTime");
            if (timeZone != null && dateTime != null) {
                try {
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS");
                    parser.setTimeZone(TimeZone.getTimeZone(convertTimezoneFromExchange(timeZone)));
                    return parser.parse(dateTime);
                } catch (ParseException e) {
                    throw new DavMailException("EXCEPTION_INVALID_DATE", dateTime);
                }
            }
        }
        return null;
    }

    /**
     * Compute recurrenceId property based on original start and timezone.
     * @return recurrenceId property
     * @throws DavMailException on error
     */
    public VProperty getRecurrenceId() throws DavMailException {
        // get the unmodified start date and timezone of occurrence
        String originalStartTimeZone = optString("originalStartTimeZone");
        String originalStart = optString("originalStart");

        if (originalStartTimeZone != null && originalStart != null && originalStart.length() >= 19) {
            // Convert date from graph to caldav format, keep timezone information
            VProperty recurrenceId = new VProperty("RECURRENCE-ID", DateUtil.convertDateFormat(originalStart.substring(0, 19), GRAPH_DATE_TIME, CALDAV_DATE_TIME));
            recurrenceId.setParam("TZID", originalStartTimeZone);
            return recurrenceId;
        } else {
            throw new DavMailException("LOG_MESSAGE", "Missing original start date and timezone");
        }
    }

    /**
     * Convert Exchange timezone id to standard timezone id.
     * Standard timezones use the area/location format.
     * @param exchangeTimezone Exchange / O365 timezone id
     * @return standard timezone
     */
    public static String convertTimezoneFromExchange(String exchangeTimezone) {
        ResourceBundle tzidsBundle = ResourceBundle.getBundle("stdtimezones");
        if (tzidsBundle.containsKey(exchangeTimezone)) {
            return tzidsBundle.getString(exchangeTimezone);
        } else {
            return exchangeTimezone;
        }
    }

    public boolean optBoolean(String key) {
        return jsonObject.optBoolean(key);
    }

    public int optInt(String key) {
        return jsonObject.optInt(key);
    }

    public String toString(int indentFactor) throws JSONException {
        return jsonObject.toString(indentFactor);
    }
}

