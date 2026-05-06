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
import davmail.exchange.VObject;
import davmail.exchange.VProperty;
import davmail.util.DateUtil;
import davmail.util.StringUtil;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
        // use field mapping to get value
        return optString(GraphField.get(key));
    }

    public String optString(GraphField field) {
        String key = field.getGraphId();
        if (key == null) {
            return null;
        }
        String value = null;
        // special case for keywords/categories
        if ("keywords".equals(key) || "categories".equals(key)) {
            JSONArray categoriesArray = jsonObject.optJSONArray("categories");
            HashSet<String> keywords = new HashSet<>();
            // Collects keywords from the categories array, joins into comma‑separated string
            if (categoriesArray != null) {
                for (int j = 0; j < categoriesArray.length(); j++) {
                    keywords.add(categoriesArray.optString(j));
                }
                value = StringUtil.join(keywords, ",");
            }
        } else if ("from".equals(key)) {
            value = formatEmailAddress(jsonObject.optJSONObject(key));
        } else if ("@odata.etag".equals(key)) {
            // tasks don't have an etag field, use @odata.etag
            String odataEtag = jsonObject.optString("@odata.etag");
            if (odataEtag != null && odataEtag.startsWith("W/\"") && odataEtag.endsWith("\"")) {
                value = odataEtag.substring(3, odataEtag.length() - 1);
            }
        } else if (!field.isExtended()) {
            // grab value by key
            value = jsonObject.optString(key, null);
        } else {
            JSONArray singleValueExtendedProperties = jsonObject.optJSONArray("singleValueExtendedProperties");
            if (singleValueExtendedProperties != null) {
                // Iterates extended properties to find a matching value
                for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                    JSONObject singleValueObject = singleValueExtendedProperties.optJSONObject(i);
                    if (singleValueObject != null && key.equals(singleValueObject.optString("id"))) {
                        value = singleValueObject.optString("value");
                    }
                }
            }
        }
        if (field.isDate()) {
            try {
                value = GraphExchangeSession.convertDateFromExchange(value);
            } catch (DavMailException e) {
                LOGGER.warn("Invalid date " + value + " on field " + key);
            }
        }
        return value;
    }

    protected String formatEmailAddress(JSONObject jsonObject) {
        String value = null;
        if (jsonObject != null) {
            JSONObject jsonEmailAddress = jsonObject.optJSONObject("emailAddress");
            if (jsonEmailAddress != null) {
                value = jsonEmailAddress.optString("name", "") + " <" + jsonEmailAddress.optString("address", "") + ">";
            }
        }
        return value;
    }

    public JSONArray optJSONArray(String key) {
        return jsonObject.optJSONArray(key);
    }

    /**
     * Set value for alias.
     * First map property alias to a field to determine graph id, then set graph property or singleValueExtendedProperty
     * @param alias field alias
     * @param value property value
     * @throws JSONException on error
     */
    public void put(String alias, String value) throws JSONException {
        GraphField field = GraphField.get(alias);
        String key = field.getGraphId();
        // handle MAPI extended fields
        if (field.isExtended()) {
            // force number attributes value
            if (field.isNumber() && value == null) {
                value = "0";
            }
            if (field.isBoolean() && value == null) {
                value = "false";
            }
            getSingleValueExtendedProperties().put(new JSONObject().put("id", key).put("value", value == null ? JSONObject.NULL : value));
        } else {
            jsonObject.put(key, value == null ? JSONObject.NULL : value);
        }
    }

    /**
     * Set the boolean value on the field defined by alias.
     * First map property alias to a field to determine graph id, then set graph property or singleValueExtendedProperty
     * @param alias field alias
     * @param value property value
     * @throws JSONException on error
     */
    public void put(String alias, boolean value) throws JSONException {
        GraphField field = GraphField.get(alias);
        String key = field.getGraphId();
        // extended field values go under singleValueExtendedProperties
        if (field.isExtended()) {
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
     * Get a singleValueExtendedProperties JSON array.
     * Create an empty JSON array on the first call.
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
     * Get optional parameter from JSON property.
     * @param section JSON property name
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

    public boolean getBoolean(String key) throws JSONException {
        return getBoolean(GraphField.get(key));
    }

    public boolean optBoolean(String key) {
        return optBoolean(GraphField.get(key));
    }

    public boolean getBoolean(GraphField field) throws JSONException {
        String key = field.getGraphId();
        if (key == null) {
            return false;
        }
        return jsonObject.getBoolean(key);
    }

    public boolean optBoolean(GraphField field) {
        String key = field.getGraphId();
        if (key == null) {
            return false;
        }
        return jsonObject.optBoolean(key);
    }

    public JSONObject optJSONObject(String key) {
        return jsonObject.optJSONObject(key);
    }

    /**
     * Convert datetimetimezone to java date.
     * Graph API returns some dates as json object with dateTime and timeZone, see
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
     * Compute recurrenceId property based on the original start and timezone.
     * @return recurrenceId property
     * @throws DavMailException on error
     */
    public VProperty getRecurrenceId() throws DavMailException {
        // get the unmodified start date and timezone of occurrence
        String originalStartTimeZone = optString("originalStartTimeZone");
        String originalStart = optString("originalStart");
        String append = "Z";

        if (originalStart != null) {
            // Per https://learn.microsoft.com/en-us/graph/api/resources/recurrencerange?view=graph-rest-1.0, originalStart is always in
            // ISO8601 format, which means originalStartTimeZone is not what should be used to convert the date.
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                originalStart = formatter.format(parser.parse(originalStart));
            } catch (ParseException e) {
                LOGGER.warn("Invalid Recurrence Date: " + originalStart);
                // Fall back to just using first 19 characters
                append = "";
            }
            // Convert date from graph to caldav format, keep timezone information
            VProperty recurrenceId = new VProperty("RECURRENCE-ID", DateUtil.convertDateFormat(originalStart.substring(0,19), GRAPH_DATE_TIME, CALDAV_DATE_TIME) + append);
            recurrenceId.setParam("TZID", originalStartTimeZone);
            LOGGER.warn("getRecurrenceId: " + originalStart + ", " + originalStartTimeZone + ", " + recurrenceId);
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


    public int optInt(String key) {
        return jsonObject.optInt(key);
    }

    public String toString(int indentFactor) throws JSONException {
        return jsonObject.toString(indentFactor);
    }

    protected static final Map<String, String> vTodoToTaskStatusMap = new HashMap<>();
    protected static final Map<String, String> taskTovTodoStatusMap = new HashMap<>();
    static {
        // The possible values are: notStarted, inProgress, completed, waitingOnOthers, deferred
        //taskTovTodoStatusMap.put("notStarted", null);
        taskTovTodoStatusMap.put("inProgress", "IN-PROCESS");
        taskTovTodoStatusMap.put("completed", "COMPLETED");
        taskTovTodoStatusMap.put("waitingOnOthers", "NEEDS-ACTION");
        taskTovTodoStatusMap.put("deferred", "CANCELLED");

        //vTodoToTaskStatusMap.put(null, "NotStarted");
        vTodoToTaskStatusMap.put("IN-PROCESS", "inProgress");
        vTodoToTaskStatusMap.put("COMPLETED", "completed");
        vTodoToTaskStatusMap.put("NEEDS-ACTION", "waitingOnOthers");
        vTodoToTaskStatusMap.put("CANCELLED", "deferred");
    }

    public void setTaskStatusFromVTodo(VObject vEvent) throws JSONException {
        String taskStatus = vTodoToTaskStatusMap.get(vEvent.getPropertyValue("STATUS"));
        if (taskStatus == null) {
            taskStatus = "notStarted";
        }
        put("status", taskStatus);
    }

    public String getVTodoStatusFromTask() {
        return taskTovTodoStatusMap.get(optString("status"));
    }

    protected static final Map<String, String> importanceToPriorityMap = new HashMap<>();

    static {
        importanceToPriorityMap.put("high", "1");
        importanceToPriorityMap.put("normal", "5");
        importanceToPriorityMap.put("low", "9");
    }

    protected static final Map<String, String> priorityToImportanceMap = new HashMap<>();

    static {
        // 0 means undefined, map it to normal
        priorityToImportanceMap.put("0", "normal");

        priorityToImportanceMap.put("1", "high");
        priorityToImportanceMap.put("2", "high");
        priorityToImportanceMap.put("3", "high");
        priorityToImportanceMap.put("4", "normal");
        priorityToImportanceMap.put("5", "normal");
        priorityToImportanceMap.put("6", "normal");
        priorityToImportanceMap.put("7", "low");
        priorityToImportanceMap.put("8", "low");
        priorityToImportanceMap.put("9", "low");
    }

    protected String getTaskPriority() {
        String taskImportance = optString("importance");
        String taskPriority = null;
        if (taskImportance != null) {
            taskPriority = importanceToPriorityMap.get(taskImportance);
        }
        return taskPriority;
    }

    public void setTaskImportanceFromVTodo(VObject vEvent) throws JSONException {
        String taskImportance = priorityToImportanceMap.get(vEvent.getPropertyValue("PRIORITY"));
        if (taskImportance == null) {
            taskImportance = "normal";
        }
        put("importance", taskImportance);
    }

}

