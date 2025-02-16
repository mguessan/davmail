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
import davmail.exception.HttpNotFoundException;
import davmail.exchange.ExchangeSession;
import davmail.exchange.auth.O365Token;
import davmail.exchange.ews.EwsExchangeSession;
import davmail.exchange.ews.ExtendedFieldURI;
import davmail.exchange.ews.Field;
import davmail.exchange.ews.FieldURI;
import davmail.exchange.ews.IndexedFieldURI;
import davmail.http.HttpClientAdapter;
import davmail.util.IOUtil;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class GraphExchangeSessionDraft extends ExchangeSession {
    HttpClientAdapter httpClient;
    O365Token token;

    /**
     * API version
     */
    String apiVersion = "beta";

    String baseUrl;

    protected class Folder extends ExchangeSession.Folder {
        public String lastModified;
        public String id;
    }

    protected static final HashSet<FieldURI> FOLDER_PROPERTIES = new HashSet<>();

    static {
        FOLDER_PROPERTIES.add(Field.get("urlcompname"));
        FOLDER_PROPERTIES.add(Field.get("folderDisplayName"));
        FOLDER_PROPERTIES.add(Field.get("lastmodified"));
        FOLDER_PROPERTIES.add(Field.get("folderclass"));
        FOLDER_PROPERTIES.add(Field.get("ctag"));
        //FOLDER_PROPERTIES.add(Field.get("count"));
        //FOLDER_PROPERTIES.add(Field.get("unread"));
        //FOLDER_PROPERTIES.add(Field.get("hassubs"));
        FOLDER_PROPERTIES.add(Field.get("uidNext"));
        FOLDER_PROPERTIES.add(Field.get("highestUid"));
    }


    public GraphExchangeSessionDraft(HttpClientAdapter httpClient, O365Token token, String userName) {
        this.httpClient = httpClient;
        this.token = token;
        this.userName = userName;
        // TODO: build url from settings for .us and other tenants
        this.baseUrl = Settings.GRAPH_URL;
    }

    // get folder id, well known folders
    // https://learn.microsoft.com/en-us/graph/api/resources/mailfolder?view=graph-rest-1.0
    // /me/mailFolders/msgfolderroot

    public Folder getFolderByName(String folderName) throws URISyntaxException, IOException, JSONException {
        Folder folder = null;

        HttpRequestBase httpRequest = new GraphRequestBuilder()
                .setMethod("GET")
                .setAccessToken(token.getAccessToken())
                .setObjectType("mailFolders")
                .setObjectId(folderName)
                .setExpandFields(FOLDER_PROPERTIES).build();

        JSONObject jsonResponse = executeRequest(httpRequest);


        folder = new Folder();
        folder.folderPath = folderName;
        folder.displayName = jsonResponse.optString("displayName");

        LOGGER.debug("urlcompname " + Field.get("urlcompname").getGraphId());
        LOGGER.debug("folderDisplayName " + jsonResponse.optString("displayName"));
        LOGGER.debug("lastmodified " + Field.get("lastmodified").getGraphId());
        LOGGER.debug("folderclass " + Field.get("folderclass").getGraphId());
        LOGGER.debug("ctag " + Field.get("ctag").getGraphId());
        LOGGER.debug("count " + Field.get("count").getGraphId());
        LOGGER.debug("unread " + Field.get("unread").getGraphId());
        LOGGER.debug("hassubs " + Field.get("hassubs").getGraphId());
        LOGGER.debug("uidNext " + Field.get("uidNext").getGraphId());
        LOGGER.debug("highestUid " + Field.get("highestUid").getGraphId());

        // retrieve property values
        JSONArray singleValueExtendedProperties = jsonResponse.optJSONArray("singleValueExtendedProperties");
        if (singleValueExtendedProperties != null) {
            for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                JSONObject singleValueProperty = singleValueExtendedProperties.getJSONObject(i);
                String singleValueId = singleValueProperty.getString("id");
                String singleValue = singleValueProperty.getString("value");
                if (Field.get("lastmodified").getGraphId().equals(singleValueId)) {
                    // TODO parse date ?
                    folder.lastModified = singleValue;
                } else if (Field.get("folderclass").getGraphId().equals(singleValueId)) {
                    folder.folderClass = singleValue;
                } else if (Field.get("uidNext").getGraphId().equals(singleValueId)) {
                    folder.uidNext = Long.parseLong(singleValue);
                } else if (Field.get("ctag").getGraphId().equals(singleValueId)) {
                    folder.ctag = singleValue;
                    // replaced with native properties
                    //} else if (Field.get("count").getGraphId().equals(singleValueId)) {
                    //    folder.count = Integer.parseInt(singleValue);
                    //} else if (Field.get("hassubs").getGraphId().equals(singleValueId)) {
                    //    folder.hasChildren = "true".equals(singleValue);
                    //} else if (Field.get("unread").getGraphId().equals(singleValueId)) {
                    //    folder.unreadCount = Integer.parseInt(singleValue);
                } else {
                    LOGGER.warn("Unknown property " + singleValueId);
                }

            }
        }
        folder.count = jsonResponse.getInt("totalItemCount");
        folder.unreadCount = jsonResponse.getInt("unreadItemCount");
        folder.hasChildren = jsonResponse.getInt("childFolderCount") > 0;

        return folder;
    }

    /**
     * Compute expand parameters from properties
     * @param fields
     * @return $expand value
     */
    private String buildExpand(HashSet<FieldURI> fields) {
        ArrayList<String> singleValueProperties = new ArrayList<>();
        ArrayList<String> multiValueProperties = new ArrayList<>();
        for (FieldURI fieldURI : fields) {
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

    public void appendExpandProperties(StringBuilder buffer, List<String> properties) {
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


    @Override
    public void close() {

    }

    @Override
    public String formatSearchDate(Date date) {
        return null;
    }

    @Override
    protected void buildSessionInfo(URI uri) throws IOException {

    }

    @Override
    public Message createMessage(String folderPath, String messageName, HashMap<String, String> properties, MimeMessage mimeMessage) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mimeMessage.writeTo(baos);
        } catch (MessagingException e) {
            throw new IOException(e.getMessage());
        }
        baos.close();
        byte[] mimeContent = IOUtil.encodeBase64(baos.toByteArray());
        // https://learn.microsoft.com/en-us/graph/api/user-post-messages

        try {
            String folderId = getFolderIdIfExists(folderPath);
            String path = "/beta/me/mailFolders/" + folderId + "/messages";
            path = "/beta/me/messages";
            HttpPost httpPost = new HttpPost(new URIBuilder(baseUrl).setPath(path).build());
            httpPost.setHeader("Content-Type", "text/plain");


            httpPost.setEntity(new ByteArrayEntity(mimeContent));
            JSONObject response = executeRequest(httpPost);

            //path = "/beta/me/mailFolders/"+response.get("id")+"/messages";
            path = "/beta/me/messages/" + response.get("id");
            HttpPatch httpPatch = new HttpPatch(new URIBuilder(baseUrl).setPath(path).build());
            httpPatch.setHeader("Content-Type", "application/json");

            // TODO: map properties
            response.put("singleValueExtendedProperties", new JSONArray().put(
                    new JSONObject()
                            .put("id", Field.get("messageFlags").getGraphId())
                            .put("value", "4")
            ));


            httpPatch.setEntity(new ByteArrayEntity(response.toString().getBytes(StandardCharsets.UTF_8)));
            response = executeRequest(httpPatch);
            response = moveMessage(response.getString("id"), folderId);
            getMessage(response.getString("id"));

            /*

            getMessage(response.getString("id"));
            response = updateMessage(response.getString("id"), properties);
            getMessage(response.getString("id"));
            getMessageBody(response.getString("id"));
            System.out.println(response.toString(4));*/


        } catch (URISyntaxException | JSONException e) {
            throw new IOException(e);
        }

        return null;
        /*
          // TODO: fields
        List<FieldUpdate> fieldUpdates = buildProperties(properties);
        if (!properties.containsKey("draft")) {
            // need to force draft flag to false
            if (properties.containsKey("read")) {
                fieldUpdates.add(Field.createFieldUpdate("messageFlags", "1"));
            } else {
                fieldUpdates.add(Field.createFieldUpdate("messageFlags", "0"));
            }
        }
        fieldUpdates.add(Field.createFieldUpdate("urlcompname", messageName));
        item.setFieldUpdates(fieldUpdates);
        */


        /*
        CreateItemMethod createItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, getFolderId(folderPath), item);
        executeMethod(createItemMethod);

        ItemId newItemId = new ItemId(createItemMethod.getResponseItem());
        GetItemMethod getItemMethod = new GetItemMethod(BaseShape.ID_ONLY, newItemId, false);
        for (String attribute : IMAP_MESSAGE_ATTRIBUTES) {
            getItemMethod.addAdditionalProperty(Field.get(attribute));
        }
        executeMethod(getItemMethod);

        return buildMessage(getItemMethod.getResponseItem());

         */
    }

    private JSONObject updateMessage(String id, HashMap<String, String> properties) throws IOException {
        try {
            String path = "/beta/me/messages/" + id;
            HttpPatch httpPatch = new HttpPatch(new URIBuilder(baseUrl).setPath(path).build());
            httpPatch.setHeader("Content-Type", "application/json");

            JSONObject jsonObject = new JSONObject();
            // TODO: map properties
            jsonObject.put("isDraft", false);
            jsonObject.put("singleValueExtendedProperties", new JSONArray().put(
                    new JSONObject()
                            .put("id", Field.get("messageFlags").getGraphId())
                            .put("value", "4")
            ));


            httpPatch.setEntity(new ByteArrayEntity(jsonObject.toString().getBytes(StandardCharsets.UTF_8)));
            JSONObject response = executeRequest(httpPatch);
            return response;

        } catch (URISyntaxException | JSONException e) {
            throw new IOException(e);
        }
    }

    private JSONObject moveMessage(String id, String folderId) throws IOException {
        try {
            String path = "/beta/me/messages/" + id + "/move/";
            HttpPost httpPost = new HttpPost(new URIBuilder(baseUrl).setPath(path).build());
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("destinationId", folderId);

            httpPost.setEntity(new ByteArrayEntity(jsonObject.toString().getBytes(StandardCharsets.UTF_8)));
            JSONObject response = executeRequest(httpPost);
            return response;

        } catch (URISyntaxException | JSONException e) {
            throw new IOException(e);
        }
    }

    private Message getMessage(String id) throws URISyntaxException, IOException, JSONException {
        HashSet<FieldURI> messageProperties = new HashSet<>();
        messageProperties.add(Field.get("messageFlags"));
        messageProperties.add(Field.get("imapUid"));
        Message message = null;
        URIBuilder uriBuilder = new URIBuilder(baseUrl)
                .setPath("/beta/me/messages/" + id)
                .addParameter("$expand", buildExpand(messageProperties));
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", "Bearer " + token.getAccessToken());
        try (
                CloseableHttpResponse response = httpClient.execute(httpGet);
        ) {
            JSONObject jsonResponse = new JsonResponseHandler().handleResponse(response);

            JSONArray singleValueExtendedProperties = jsonResponse.optJSONArray("singleValueExtendedProperties");
            if (singleValueExtendedProperties != null) {
                for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                    JSONObject singleValueProperty = singleValueExtendedProperties.getJSONObject(i);
                    String singleValueId = singleValueProperty.getString("id");
                    String singleValue = singleValueProperty.getString("value");
                    if (Field.get("messageFlags").getGraphId().equals(singleValueId)) {
                        System.out.println("messageFlags: " + singleValue);
                    } else if (Field.get("imapUid").getGraphId().equals(singleValueId)) {
                        System.out.println("imapUid: " + singleValue);
                    } else {
                        LOGGER.warn("Unknown property " + singleValueId);
                    }

                }


            }
        }
        return message;
    }

    public void getMessageBody(String id) throws URISyntaxException, IOException, MessagingException {


        MimeMessage mimeMessage = null;
        URIBuilder uriBuilder = new URIBuilder(baseUrl)
                .setPath("/beta/me/messages/" + id + "/$value");
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", "Bearer " + token.getAccessToken());
        try (
                CloseableHttpResponse response = httpClient.execute(httpGet);
        ) {
            try (InputStream inputStream = response.getEntity().getContent()) {
                if (HttpClientAdapter.isGzipEncoded(response)) {
                    mimeMessage = new MimeMessage(null, new SharedByteArrayInputStream(IOUtil.readFully(new GZIPInputStream(inputStream))));
                } else {
                    mimeMessage = new MimeMessage(null, new SharedByteArrayInputStream(IOUtil.readFully(inputStream)));
                }
            } catch (MessagingException e) {
                throw new IOException(e.getMessage(), e);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            mimeMessage.writeTo(baos);
            System.out.println(baos.toString("UTF-8"));

        }
    }

    @Override
    public void updateMessage(Message message, Map<String, String> properties) throws IOException {

    }

    @Override
    public void deleteMessage(Message message) throws IOException {

    }

    @Override
    protected byte[] getContent(Message message) throws IOException {
        return new byte[0];
    }

    @Override
    public MessageList searchMessages(String folderName, Set<String> attributes, Condition condition) throws IOException {
        return null;
    }

    @Override
    public MultiCondition and(Condition... condition) {
        return null;
    }

    @Override
    public MultiCondition or(Condition... condition) {
        return null;
    }

    @Override
    public Condition not(Condition condition) {
        return null;
    }

    @Override
    public Condition isEqualTo(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition isEqualTo(String attributeName, int value) {
        return null;
    }

    @Override
    public Condition headerIsEqualTo(String headerName, String value) {
        return null;
    }

    @Override
    public Condition gte(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition gt(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition lt(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition lte(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition contains(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition startsWith(String attributeName, String value) {
        return null;
    }

    @Override
    public Condition isNull(String attributeName) {
        return null;
    }

    @Override
    public Condition exists(String attributeName) {
        return null;
    }

    @Override
    public Condition isTrue(String attributeName) {
        return null;
    }

    @Override
    public Condition isFalse(String attributeName) {
        return null;
    }

    @Override
    public List<ExchangeSession.Folder> getSubFolders(String folderName, Condition condition, boolean recursive) throws IOException {
        // TODO implement conditions and recursive search
        ArrayList<ExchangeSession.Folder> folders = new ArrayList<>();
        try {
            String folderId = getFolderId(folderName);
            HttpGet httpGet = new HttpGet(new URIBuilder(baseUrl).setPath("/beta/me/mailFolders/" + folderId + "/childFolders")
                    .addParameter("$expand", buildExpand(FOLDER_PROPERTIES))
                    .build());
            JSONObject jsonResponse = executeRequest(httpGet);
            JSONArray jsonValues = jsonResponse.optJSONArray("value");
            for (int i = 0; i < jsonValues.length(); i++) {
                Folder folder = buildFolder(jsonValues.getJSONObject(i));
                folder.folderPath = folderName + '/' + EwsExchangeSession.encodeFolderName(folder.displayName);
                folders.add(folder);
            }
        } catch (JSONException | URISyntaxException e) {
            throw new IOException(e);
        }
        return folders;
    }

    @Override
    public void sendMessage(MimeMessage mimeMessage) throws IOException, MessagingException {

    }

    @Override
    protected Folder internalGetFolder(String folderPath) throws IOException {
        String folderId = getFolderId(folderPath);

        JSONObject jsonResponse = null;
        try {
            URIBuilder uriBuilder = new URIBuilder(baseUrl)
                    .setPath("/beta/me/mailFolders/" + folderId)
                    .addParameter("$expand", buildExpand(FOLDER_PROPERTIES));
            HttpGet httpGet = new HttpGet(uriBuilder.build());
            httpGet.setHeader("Authorization", "Bearer " + token.getAccessToken());
            try (
                    CloseableHttpResponse response = httpClient.execute(httpGet);
            ) {
                jsonResponse = new JsonResponseHandler().handleResponse(response);
            }
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }

        GraphExchangeSessionDraft.Folder folder;
        if (jsonResponse != null) {
            folder = buildFolder(jsonResponse);
            folder.folderPath = folderPath;
        } else {
            throw new HttpNotFoundException("Folder " + folderPath + " not found");
        }
        return folder;
    }

    private String internalGetFolderId(String folderName) throws IOException {
        String folderId;
        try {
            URIBuilder uriBuilder = new URIBuilder(baseUrl)
                    .setPath("/beta/me/mailFolders/" + folderName)
                    .addParameter("$select", "id");
            HttpGet httpGet = new HttpGet(uriBuilder.build());
            httpGet.setHeader("Authorization", "Bearer " + token.getAccessToken());
            try (
                    CloseableHttpResponse response = httpClient.execute(httpGet);
            ) {
                JSONObject jsonResponse = new JsonResponseHandler().handleResponse(response);
                folderId = jsonResponse.getString("id");
            }
        } catch (URISyntaxException | JSONException e) {
            throw new IOException(e);
        }
        return folderId;
    }

    protected String getFolderIdIfExists(String folderPath) throws IOException {
        // assume start from root folder
        // TODO: implement access to shared mailbox
        String parentFolderId = internalGetFolderId("msgfolderroot");
        if ("msgfolderroot".equals(folderPath)) {
            return parentFolderId;
        }
        String folderId = null;
        String[] pathElements = folderPath.split("/");
        for (String pathElement : pathElements) {
            try {
                String displayName = EwsExchangeSession.decodeFolderName(pathElement);
                URIBuilder uriBuilder = new URIBuilder(baseUrl)
                        .setPath("/beta/me/mailFolders/" + parentFolderId + "/childFolders")
                        .addParameter("$select", "id, displayName")
                        // TODO escape quotes
                        .addParameter("$filter", "displayName eq '" + displayName + "'");
                HttpGet httpGet = new HttpGet(uriBuilder.build());
                httpGet.setHeader("Authorization", "Bearer " + token.getAccessToken());
                try (
                        CloseableHttpResponse response = httpClient.execute(httpGet);
                ) {
                    JSONObject jsonResponse = new JsonResponseHandler().handleResponse(response);
                    JSONArray jsonFolders = jsonResponse.getJSONArray("value");
                    String currentFolderId = null;
                    for (int i = 0; i < jsonFolders.length(); i++) {
                        JSONObject jsonFolder = jsonFolders.getJSONObject(i);
                        if (displayName.equals(jsonFolder.optString("displayName"))) {
                            // found folder
                            currentFolderId = jsonFolder.getString("id");
                        }
                    }
                    parentFolderId = currentFolderId;
                    if (currentFolderId == null) {
                        // not found
                        break;
                    }
                }
            } catch (URISyntaxException | JSONException e) {
                throw new IOException(e);
            }
        }
        return parentFolderId;
    }

    protected String getFolderId(String folderPath) throws IOException {
        String folderId = getFolderIdIfExists(folderPath);
        if (folderId == null) {
            throw new HttpNotFoundException("Folder '" + folderPath + "' not found");
        }
        return folderId;
    }

    private Folder buildFolder(JSONObject jsonResponse) throws IOException {
        Folder folder = new Folder();
        try {
            folder.displayName = jsonResponse.optString("displayName");
            folder.count = jsonResponse.getInt("totalItemCount");
            folder.unreadCount = jsonResponse.getInt("unreadItemCount");
            folder.hasChildren = jsonResponse.getInt("childFolderCount") > 0;

            folder.id = jsonResponse.getString("id");

            // retrieve property values
            JSONArray singleValueExtendedProperties = jsonResponse.optJSONArray("singleValueExtendedProperties");
            if (singleValueExtendedProperties != null) {
                for (int i = 0; i < singleValueExtendedProperties.length(); i++) {
                    JSONObject singleValueProperty = singleValueExtendedProperties.getJSONObject(i);
                    String singleValueId = singleValueProperty.getString("id");
                    String singleValue = singleValueProperty.getString("value");
                    if (Field.get("lastmodified").getGraphId().equals(singleValueId)) {
                        // TODO parse date ?
                        folder.lastModified = singleValue;
                    } else if (Field.get("folderclass").getGraphId().equals(singleValueId)) {
                        folder.folderClass = singleValue;
                    } else if (Field.get("uidNext").getGraphId().equals(singleValueId)) {
                        folder.uidNext = Long.parseLong(singleValue);
                    } else if (Field.get("ctag").getGraphId().equals(singleValueId)) {
                        folder.ctag = singleValue;
                    } else {
                        LOGGER.warn("Unknown property " + singleValueId);
                    }

                }
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
        return folder;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int createFolder(String folderPath, String folderClass, Map<String, String> properties) throws IOException {
        // TODO: handle path, decodeFolderName
        if ("IPF.Appointment".equals(folderClass) && folderPath.startsWith("calendars")) {
            // create calendar
            try {
                HttpPost httpPost = new HttpPost(new URIBuilder(baseUrl).setPath("/beta/me/calendars").build());
                httpPost.setHeader("Content-Type", "application/json");
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", folderPath);

                httpPost.setEntity(new ByteArrayEntity(jsonObject.toString().getBytes(StandardCharsets.UTF_8)));
                executeRequest(httpPost);

            } catch (URISyntaxException | JSONException e) {
                throw new IOException(e);
            }
        } else {
            String parentFolderId;
            String folderName;
            if (folderPath.contains("/")) {
                String parentFolderPath = folderPath.substring(0, folderPath.lastIndexOf('/'));
                parentFolderId = getFolderId(parentFolderPath);
                folderName = EwsExchangeSession.decodeFolderName(folderPath.substring(folderPath.lastIndexOf('/') + 1));
            } else {
                parentFolderId = getFolderId("msgfolderroot");
                folderName = EwsExchangeSession.decodeFolderName(folderPath);
            }

            try {
                HttpPost httpPost = new HttpPost(new URIBuilder(baseUrl).setPath("/beta/me/mailFolders/" + parentFolderId + "/childFolders").build());
                httpPost.setHeader("Content-Type", "application/json");
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("displayName", folderName);
                if (folderClass != null) {
                    JSONArray singleValueExtendedProperties = new JSONArray();
                    singleValueExtendedProperties.put(new JSONObject().put("id", Field.get("folderclass").getGraphId()).put("value", folderClass));
                    jsonObject.put("singleValueExtendedProperties", singleValueExtendedProperties);
                }

                httpPost.setEntity(new ByteArrayEntity(jsonObject.toString().getBytes(StandardCharsets.UTF_8)));
                executeRequest(httpPost);

            } catch (URISyntaxException | JSONException e) {
                throw new IOException(e);
            }
        }

        return HttpStatus.SC_CREATED;
    }

    @Override
    public int updateFolder(String folderName, Map<String, String> properties) throws IOException {
        return 0;
    }

    @Override
    public void deleteFolder(String folderPath) throws IOException {
        String folderId = getFolderId(folderPath);

        try {
            HttpDelete httpDelete = new HttpDelete(new URIBuilder(baseUrl).setPath("/beta/me/mailFolders/" + folderId).build());
            executeRequest(httpDelete);

        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void copyMessage(Message message, String targetFolder) throws IOException {

    }

    @Override
    public void moveMessage(Message message, String targetFolder) throws IOException {

    }

    @Override
    public void moveFolder(String folderName, String targetName) throws IOException {

    }

    @Override
    public void moveItem(String sourcePath, String targetPath) throws IOException {

    }

    @Override
    protected void moveToTrash(Message message) throws IOException {

    }

    @Override
    protected Set<String> getItemProperties() {
        return null;/*EwsExchangeSession.ITEM_PROPERTIES;*/
    }

    @Override
    public List<Contact> searchContacts(String folderPath, Set<String> attributes, Condition condition, int maxCount) throws IOException {
        return null;
    }

    @Override
    public List<Event> getEventMessages(String folderPath) throws IOException {
        return null;
    }

    @Override
    protected Condition getCalendarItemCondition(Condition dateCondition) {
        return null;
    }

    @Override
    public List<Event> searchEvents(String folderPath, Set<String> attributes, Condition condition) throws IOException {
        return null;
    }

    @Override
    public Item getItem(String folderPath, String itemName) throws IOException {
        return null;
    }

    @Override
    public ContactPhoto getContactPhoto(Contact contact) throws IOException {
        return null;
    }

    @Override
    public void deleteItem(String folderPath, String itemName) throws IOException {

    }

    @Override
    public void processItem(String folderPath, String itemName) throws IOException {

    }

    @Override
    public int sendEvent(String icsBody) throws IOException {
        return 0;
    }

    @Override
    protected Contact buildContact(String folderPath, String itemName, Map<String, String> properties, String etag, String noneMatch) throws IOException {
        return null;
    }

    @Override
    protected ItemResult internalCreateOrUpdateEvent(String folderPath, String itemName, String contentClass, String icsBody, String etag, String noneMatch) throws IOException {
        return null;
    }

    @Override
    public boolean isSharedFolder(String folderPath) {
        return false;
    }

    @Override
    public boolean isMainCalendar(String folderPath) throws IOException {
        return false;
    }

    @Override
    public Map<String, Contact> galFind(Condition condition, Set<String> returningAttributes, int sizeLimit) throws IOException {
        return null;
    }

    @Override
    protected String getFreeBusyData(String attendee, String start, String end, int interval) throws IOException {
        return null;
    }


    @Override
    protected void loadVtimezone() {
        try {
            String timezoneId = null;
            // Outlook token does not have access to mailboxSettings, use old EWS call
            JSONObject mailboxSettings = getMailboxSettings();
            if (mailboxSettings != null) {
                timezoneId = mailboxSettings.optString("timeZone", null);
            }
            // failover: use timezone id from settings file
            if (timezoneId == null) {
                timezoneId = Settings.getProperty("davmail.timezoneId");
            }
            // last failover: use GMT
            if (timezoneId == null) {
                LOGGER.warn("Unable to get user timezone, using GMT Standard Time. Set davmail.timezoneId setting to override this.");
                timezoneId = "GMT Standard Time";
            }

            // delete existing temp folder first to avoid errors
            deleteFolder("davmailtemp");
            createCalendarFolder("davmailtemp", null);
            /*

            createCalendarFolder("davmailtemp", null);
            EWSMethod.Item item = new EWSMethod.Item();
            item.type = "CalendarItem";
            if (!"Exchange2007_SP1".equals(serverVersion)) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
                dateFormatter.setTimeZone(GMT_TIMEZONE);
                Calendar cal = Calendar.getInstance();
                item.put("Start", dateFormatter.format(cal.getTime()));
                cal.add(Calendar.DAY_OF_MONTH, 1);
                item.put("End", dateFormatter.format(cal.getTime()));
                item.put("StartTimeZone", timezoneId);
            } else {
                item.put("MeetingTimeZone", timezoneId);
            }
            CreateItemMethod createItemMethod = new CreateItemMethod(MessageDisposition.SaveOnly, SendMeetingInvitations.SendToNone, getFolderId("davmailtemp"), item);
            executeMethod(createItemMethod);
            item = createItemMethod.getResponseItem();
            if (item == null) {
                throw new IOException("Empty timezone item");
            }
            VCalendar vCalendar = new VCalendar(getContent(new ItemId(item)), email, null);
            this.vTimezone = vCalendar.getVTimezone();
            // delete temporary folder
            deleteFolder("davmailtemp");*/
        } catch (IOException e) {
            LOGGER.warn("Unable to get VTIMEZONE info: " + e, e);
        } catch (URISyntaxException e) {
            LOGGER.warn("Unable to get VTIMEZONE info: " + e, e);
        }
    }

    public JSONObject getMailboxSettings() throws IOException, URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(baseUrl)
                .setPath("/beta/me/mailboxsettings");
        return executeRequest(new HttpGet(uriBuilder.build()));
    }

    /**
     * Return supported timezone list
     * @param timeZoneStandard Windows or Iana
     * @return timezone list
     * @throws IOException on error
     * @throws URISyntaxException on error
     */
    public JSONArray getSupportedTimeZones(String timeZoneStandard) throws IOException, URISyntaxException, JSONException {
        if (timeZoneStandard == null) {
            timeZoneStandard = "Windows";
        }
        URIBuilder uriBuilder = new URIBuilder(baseUrl)
                .setPath("/beta/me/outlook/supportedTimeZones(TimeZoneStandard=microsoft.graph.timeZoneStandard'" + timeZoneStandard + "')");
        JSONObject response = executeRequest(new HttpGet(uriBuilder.build()));
        return response.getJSONArray("value");
    }

    private JSONObject executeRequest(HttpRequestBase request) throws IOException {
        JSONObject jsonResponse;
        request.setHeader("Authorization", "Bearer " + token.getAccessToken());
        // disable gzip
        //httpGet.setHeader("Accept-Encoding", "");
        try (
                CloseableHttpResponse response = httpClient.execute(request);
        ) {
            jsonResponse = new JsonResponseHandler().handleResponse(response);
        }
        return jsonResponse;
    }

}
