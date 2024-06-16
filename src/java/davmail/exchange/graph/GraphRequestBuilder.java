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
import davmail.exchange.ews.ExtendedFieldURI;
import davmail.exchange.ews.FieldURI;
import davmail.exchange.ews.IndexedFieldURI;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Build Microsoft graph request
 */
public class GraphRequestBuilder {

    String method = "POST";
    String baseUrl = Settings.GRAPH_URL;
    String version = "beta";
    String username = "me";
    String objectType;

    Set<FieldURI> expandFields;

    String accessToken;

    JSONObject jsonBody = null;
    private String objectId;

    /**
     * Set property in Json body.
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
     * Set epxand fields (returning attributes).
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

    public GraphRequestBuilder setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    public GraphRequestBuilder setMethod(String method) {
        this.method = method;
        return this;
    }

    public GraphRequestBuilder setObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }

    /**
     * Build request path based on version, username, object type and object id.
     * @return request path
     */
    protected String buildPath() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(version)
                .append("/").append(username);
        if (objectType != null) {
            buffer.append("/").append(objectType);
        }
        if (objectId != null) {
            buffer.append("/").append(objectId);
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
            if (fieldURI instanceof ExtendedFieldURI) {
                singleValueProperties.add(fieldURI.getGraphId());
            } else if (fieldURI instanceof IndexedFieldURI) {
                multiValueProperties.add(fieldURI.getGraphId());
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
     * @throws URISyntaxException on error
     */
    public HttpRequestBase build() throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(baseUrl).setPath(buildPath());
        if (expandFields != null) {
            uriBuilder.addParameter("$expand", buildExpand());
        }

        HttpRequestBase httpRequest;
        if ("POST".equals(method)) {
            httpRequest = new HttpPost(uriBuilder.build());
            if (jsonBody != null) {
                ((HttpPost) httpRequest).setEntity(new ByteArrayEntity(jsonBody.toString().getBytes(StandardCharsets.UTF_8)));
            }
        } else {
            // default to GET request
            httpRequest = new HttpGet(uriBuilder.build());
        }
        httpRequest.setHeader("Content-Type", "application/json");
        httpRequest.setHeader("Authorization", "Bearer " + accessToken);

        return httpRequest;
    }

}
