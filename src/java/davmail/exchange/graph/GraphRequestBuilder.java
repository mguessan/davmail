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

import davmail.Settings;
import davmail.exchange.ExchangeSession;
import davmail.util.IOUtil;
import davmail.util.StringUtil;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Build Microsoft graph request
 */
public class GraphRequestBuilder {
    protected static final Logger LOGGER = Logger.getLogger("davmail.exchange.graph.GraphRequestBuilder");

    String method = "POST";

    String contentType = "application/json";
    String baseUrl = Settings.getGraphUrl();
    String version = Settings.getProperty("davmail.graphVersion", "beta");
    String mailbox;
    String objectType;

    String objectId;

    String childType;
    String childId;
    String childSuffix;

    String action;

    String select;
    String expand;

    String filter;
    String search;
    int sizeLimit;

    String startDateTime;
    String endDateTime;

    String timeZone;

    Set<GraphField> selectFields;

    String accessToken;

    JSONObject jsonBody = null;

    /**
     * Custom request headers
     */
    HashMap<String, String> headerMap;

    byte[] mimeContent;

    /**
     * Set property in the JSON body.
     * @param name property name
     * @param value property value
     * @throws JSONException on error
     */
    public GraphRequestBuilder setProperty(String name, String value) throws JSONException {
        if (jsonBody == null) {
            jsonBody = new JSONObject();
        }
        jsonBody.put(name, value);
        return this;
    }

    /**
     * Replace JSON body;
     * @return this
     */
    public GraphRequestBuilder setJsonBody(JSONObject jsonBody) {
        this.jsonBody = jsonBody;
        return this;
    }

    public GraphRequestBuilder setJsonBody(GraphObject graphObject) {
        this.jsonBody = graphObject.jsonObject;
        return this;
    }

    public GraphRequestBuilder addHeader(String name, String value) {
        if (headerMap == null) {
            headerMap = new HashMap<>();
        }
        headerMap.put(name, value);
        return this;
    }

    /**
     * Set expand fields (returning attributes).
     * @param selectFields set of fields to return
     * @return this
     */
    public GraphRequestBuilder setSelectFields(Set<GraphField> selectFields) {
        this.selectFields = selectFields;
        computeSelectAndExpand();
        return this;
    }

    public GraphRequestBuilder setVersion(String version) {
        this.version = version;
        return this;
    }

    public GraphRequestBuilder setObjectType(String objectType) {
        this.objectType = objectType;
        return this;
    }

    public GraphRequestBuilder setChildType(String childType) {
        this.childType = childType;
        return this;
    }

    public GraphRequestBuilder setChildId(String childId) {
        this.childId = childId;
        return this;
    }

    public GraphRequestBuilder setChildSuffix(String childSuffix) {
        this.childSuffix = childSuffix;
        return this;
    }

    public GraphRequestBuilder setAction(String action) {
        this.action = action;
        return this;
    }

    public GraphRequestBuilder setFilter(String filter) {
        this.filter = filter;
        return this;
    }

    public GraphRequestBuilder setFilter(ExchangeSession.Condition condition) {
        if (condition != null && !condition.isEmpty()) {
            StringBuilder buffer = new StringBuilder();
            condition.appendTo(buffer);
            this.filter = buffer.toString();
            LOGGER.debug("filter: " + filter);
        }
        return this;
    }

    public GraphRequestBuilder setSearch(String search) {
        this.search = search;
        return this;
    }

    public GraphRequestBuilder setStartDateTime(String startDateTime) {
        this.startDateTime = startDateTime;
        return this;
    }

    public GraphRequestBuilder setEndDateTime(String endDateTime) {
        this.endDateTime = endDateTime;
        return this;
    }

    public GraphRequestBuilder setTimezone(String timeZone) {
        this.timeZone = timeZone;
        return this;
    }

    public GraphRequestBuilder setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    public GraphRequestBuilder setMethod(String method) {
        this.method = method;
        return this;
    }

    public GraphRequestBuilder setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public GraphRequestBuilder setMimeContent(byte[] mimeContent) {
        this.mimeContent = mimeContent;
        return this;
    }

    public GraphRequestBuilder setMailbox(String mailbox) {
        this.mailbox = mailbox;
        return this;
    }

    public GraphRequestBuilder setObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }

    public GraphRequestBuilder setSelect(String select) {
        this.select = select;
        return this;
    }

    public GraphRequestBuilder setSizeLimit(int sizeLimit) {
        this.sizeLimit = sizeLimit;
        return this;
    }

    /**
     * Build request path based on version, username, object type and object id.
     * @return request path
     */
    protected String buildPath() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(version);
        if ("orgcontacts".equals(objectType)) {
            // global org contact search
            buffer.append("/contacts");
        } else if ("users".equals(objectType)) {
            buffer.append("/users");
        } else {
            if (mailbox != null) {
                buffer.append("/users/").append(mailbox);
            } else {
                buffer.append("/me");
            }
            if (objectType != null) {
                buffer.append("/").append(objectType);
            }
        }
        if (objectId != null) {
            buffer.append("/").append(objectId);
        }
        if (childType != null) {
            buffer.append("/").append(childType);
        }
        if (childId != null) {
            buffer.append("/").append(childId);
        }
        if (childSuffix != null) {
            buffer.append("/").append(childSuffix);
        }
        if (action != null) {
            buffer.append("/").append(action);
        }

        return buffer.toString();
    }

    /**
     * Compute expand parameters from properties.
     */
    private void computeSelectAndExpand() {
        ArrayList<String> singleValueProperties = new ArrayList<>();
        ArrayList<String> multiValueProperties = new ArrayList<>();
        ArrayList<String> selectProperties = new ArrayList<>();
        for (GraphField field : selectFields) {
            if (field.isExtended()) {
                if (field.isMultiValued()) {
                    multiValueProperties.add(field.getGraphId());
                } else {
                    singleValueProperties.add(field.getGraphId());
                    if (field.getAlias().startsWith("smtpemail")) {
                        // email fetched, load emailAddresses array
                        selectProperties.add("emailAddresses");
                    }
                }
            // etag is always returned, no a select field
            } else if (!GraphField.getGraphId("@odata.etag").equals(field.getGraphId())){
                selectProperties.add(field.getGraphId());
            }
        }
        StringBuilder expandBuffer = new StringBuilder();
        if (!singleValueProperties.isEmpty()) {
            expandBuffer.append("singleValueExtendedProperties($filter=");
            appendExpandProperties(expandBuffer, singleValueProperties);
            expandBuffer.append(")");
        }
        if (!multiValueProperties.isEmpty()) {
            if (!singleValueProperties.isEmpty()) {
                expandBuffer.append(",");
            }
            expandBuffer.append("multiValueExtendedProperties($filter=");
            appendExpandProperties(expandBuffer, multiValueProperties);
            expandBuffer.append(")");
        }
        expand = expandBuffer.toString();
        if (!selectProperties.isEmpty()) {
            select = String.join(",", selectProperties);
        }
    }

    /**
     * Build expand graph parameter to retrieve mapi properties.
     * @param buffer expand buffer
     * @param properties MAPI properties list
     */
    protected void appendExpandProperties(StringBuilder buffer, List<String> properties) {
        boolean first = true;
        for (String id : properties) {
            if (first) {
                first = false;
            } else {
                buffer.append(" or ");
            }
            buffer.append("id eq '").append(id).append("'");
        }
    }


    /**
     * Build http request.
     * @return Http request
     * @throws IOException on error
     */
    public HttpRequestBase build() throws IOException {
        try {
            URIBuilder uriBuilder = new URIBuilder(baseUrl).setPath(buildPath());
            if (select != null) {
                uriBuilder.addParameter("$select", select);
            }

            if (selectFields != null) {
                uriBuilder.addParameter("$expand", expand);
            }

            if (filter != null) {
                uriBuilder.addParameter("$filter", filter);
            }

            if (search != null) {
                uriBuilder.addParameter("$search", "\""+ StringUtil.escapeDoubleQuotes(search)+"\"");
            }

            if (startDateTime != null) {
                uriBuilder.addParameter("startDateTime", startDateTime);
            }

            if (endDateTime != null) {
                uriBuilder.addParameter("endDateTime", endDateTime);
            }

            if (sizeLimit != 0) {
                uriBuilder.addParameter("$top", String.valueOf(sizeLimit));
            }

            HttpRequestBase httpRequest;

            if ("POST".equals(method)) {
                httpRequest = new HttpPost(uriBuilder.build());
                if (mimeContent != null) {
                    ((HttpPost) httpRequest).setEntity(new ByteArrayEntity(mimeContent));
                } else if (jsonBody != null) {
                    ((HttpPost) httpRequest).setEntity(new ByteArrayEntity(IOUtil.convertToBytes(jsonBody)));
                }
            } else if (HttpPut.METHOD_NAME.equals(method)) {
                // contact picture
                httpRequest = new HttpPut(uriBuilder.build());
                if (mimeContent != null) {
                    ((HttpPut) httpRequest).setEntity(new ByteArrayEntity(mimeContent));
                }
            } else if (HttpPatch.METHOD_NAME.equals(method)) {
                httpRequest = new HttpPatch(uriBuilder.build());
                if (jsonBody != null) {
                    ((HttpPatch) httpRequest).setEntity(new ByteArrayEntity(IOUtil.convertToBytes(jsonBody)));
                }
            } else if ("DELETE".equals(method)) {
                httpRequest = new HttpDelete(uriBuilder.build());
            } else {
                // default to GET request
                httpRequest = new HttpGet(uriBuilder.build());
            }
            httpRequest.setHeader("Content-Type", contentType);
            httpRequest.setHeader("Authorization", "Bearer " + accessToken);

            // set custom headers
            if (headerMap != null) {
                for (Map.Entry<String, String> header : headerMap.entrySet()) {
                    httpRequest.addHeader(header.getKey(), header.getValue());
                }
            }

            if (timeZone != null) {
                httpRequest.addHeader("Prefer", "outlook.timezone=\"" + timeZone + "\"");
            }

            httpRequest.addHeader("Prefer", "IdType=\"ImmutableId\"");

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(httpRequest.getMethod() + " " + httpRequest.getURI());
                if (jsonBody != null) {
                    LOGGER.debug(jsonBody.toString());
                }
            }

            return httpRequest;
        } catch (URISyntaxException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

}
