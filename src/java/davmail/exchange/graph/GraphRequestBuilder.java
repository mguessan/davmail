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
import davmail.exchange.ews.ExtendedFieldURI;
import davmail.exchange.ews.FieldURI;
import davmail.util.IOUtil;
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
import java.util.List;
import java.util.Set;

/**
 * Build Microsoft graph request
 */
public class GraphRequestBuilder {
    protected static final Logger LOGGER = Logger.getLogger("davmail.exchange.graph.GraphRequestBuilder");

    String method = "POST";

    String contentType = "application/json";
    String baseUrl = Settings.GRAPH_URL;
    String version = "beta";
    String mailbox;
    String objectType;

    String childType;
    String childId;
    String childSuffix;

    String select;

    String filter;

    String startDateTime;
    String endDateTime;

    String timeZone;

    Set<FieldURI> expandFields;

    String accessToken;

    JSONObject jsonBody = null;

    byte[] mimeContent;
    private String objectId;

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

    /**
     * Set expand fields (returning attributes).
     * @param expandFields set of fields to return
     * @return this
     */
    public GraphRequestBuilder setExpandFields(Set<FieldURI> expandFields) {
        this.expandFields = expandFields;
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

    public GraphRequestBuilder setFilter(String filter) {
        this.filter = filter;
        return this;
    }

    public GraphRequestBuilder setFilter(ExchangeSession.Condition condition) {
        if (condition != null && !condition.isEmpty()) {
            StringBuilder buffer = new StringBuilder();
            condition.appendTo(buffer);
            LOGGER.debug("filter: " + filter);
            this.filter = buffer.toString();
        }
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

    /**
     * Build request path based on version, username, object type and object id.
     * @return request path
     */
    protected String buildPath() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(version);
        if (mailbox != null) {
            buffer.append("/users/").append(mailbox);
        } else {
            buffer.append("/me");
        }
        if (objectType != null) {
            buffer.append("/").append(objectType);
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

        return buffer.toString();
    }

    /**
     * Compute expand parameters from properties.
     * @return $expand value
     */
    private String buildExpand() {
        ArrayList<String> singleValueProperties = new ArrayList<>();
        ArrayList<String> multiValueProperties = new ArrayList<>();
        for (FieldURI fieldURI : expandFields) {
            if (fieldURI.isMultiValued()) {
                multiValueProperties.add(fieldURI.getGraphId());
            } else if (fieldURI instanceof ExtendedFieldURI) {
                String graphId = fieldURI.getGraphId();
                // only expanded properties need a singleValueProperties entry
                // TODO refactor fields to only use fieldURI for expanded properties
                if (graphId.contains(" ")) {
                    singleValueProperties.add(fieldURI.getGraphId());
                }
            }
        }
        StringBuilder expand = new StringBuilder();
        if (!singleValueProperties.isEmpty()) {
            expand.append("singleValueExtendedProperties($filter=");
            appendExpandProperties(expand, singleValueProperties);
            expand.append(")");
        }
        if (!multiValueProperties.isEmpty()) {
            if (!singleValueProperties.isEmpty()) {
                expand.append(",");
            }
            expand.append("multiValueExtendedProperties($filter=");
            appendExpandProperties(expand, multiValueProperties);
            expand.append(")");
        }
        return expand.toString();
    }

    /**
     * Build expand graph parameter to retrieve mapi properties.
     * @param buffer expand buffer
     * @param properties mapi properties list
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

            if (expandFields != null) {
                uriBuilder.addParameter("$expand", buildExpand());
            }

            if (filter != null) {
                uriBuilder.addParameter("$filter", filter);
            }

            if (startDateTime != null) {
                uriBuilder.addParameter("startDateTime", startDateTime);
            }

            if (endDateTime != null) {
                uriBuilder.addParameter("endDateTime", endDateTime);
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

            if (timeZone != null) {
                httpRequest.setHeader("Prefer", "outlook.timezone=\"" + timeZone + "\"");
            }

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
