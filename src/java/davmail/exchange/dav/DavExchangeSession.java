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
package davmail.exchange.dav;

import davmail.exception.DavMailAuthenticationException;
import davmail.exception.DavMailException;
import davmail.exchange.ExchangeSession;
import davmail.http.DavGatewayHttpClientFacade;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.client.methods.PropFindMethod;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.xml.Namespace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Webdav Exchange adapter.
 * Compatible with Exchange 2003 and 2007 with webdav available.
 */
public class DavExchangeSession extends ExchangeSession {

    /**
     * @inheritDoc
     */
    public DavExchangeSession(String url, String userName, String password) throws IOException {
        super(url, userName, password);
    }

    @Override
    protected void buildSessionInfo(HttpMethod method) throws DavMailException {
        buildMailPath(method);

        // get base http mailbox http urls
        getWellKnownFolders();
    }

    static final String BASE_HREF = "<base href=\"";

    protected void buildMailPath(HttpMethod method) throws DavMailAuthenticationException {
        // find base url
        String line;

        // get user mail URL from html body (multi frame)
        BufferedReader mainPageReader = null;
        try {
            mainPageReader = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
            //noinspection StatementWithEmptyBody
            while ((line = mainPageReader.readLine()) != null && line.toLowerCase().indexOf(BASE_HREF) == -1) {
            }
            if (line != null) {
                int start = line.toLowerCase().indexOf(BASE_HREF) + BASE_HREF.length();
                int end = line.indexOf('\"', start);
                String mailBoxBaseHref = line.substring(start, end);
                URL baseURL = new URL(mailBoxBaseHref);
                mailPath = baseURL.getPath();
                LOGGER.debug("Base href found in body, mailPath is " + mailPath);
                buildEmail(method.getURI().getHost());
                LOGGER.debug("Current user email is " + email);
            } else {
                // failover for Exchange 2007 : build standard mailbox link with email
                buildEmail(method.getURI().getHost());
                mailPath = "/exchange/" + email + '/';
                LOGGER.debug("Current user email is " + email + ", mailPath is " + mailPath);
            }
        } catch (IOException e) {
            LOGGER.error("Error parsing main page at " + method.getPath(), e);
        } finally {
            if (mainPageReader != null) {
                try {
                    mainPageReader.close();
                } catch (IOException e) {
                    LOGGER.error("Error parsing main page at " + method.getPath());
                }
            }
            method.releaseConnection();
        }


        if (mailPath == null || email == null) {
            throw new DavMailAuthenticationException("EXCEPTION_AUTHENTICATION_FAILED_PASSWORD_EXPIRED");
        }
    }

    protected void getWellKnownFolders() throws DavMailException {
        // Retrieve well known URLs
        MultiStatusResponse[] responses;
        try {
            responses = DavGatewayHttpClientFacade.executePropFindMethod(
                    httpClient, URIUtil.encodePath(mailPath), 0, WELL_KNOWN_FOLDERS);
            if (responses.length == 0) {
                throw new DavMailException("EXCEPTION_UNABLE_TO_GET_MAIL_FOLDER", mailPath);
            }
            DavPropertySet properties = responses[0].getProperties(HttpStatus.SC_OK);
            inboxUrl = getURIPropertyIfExists(properties, "inbox", URN_SCHEMAS_HTTPMAIL);
            deleteditemsUrl = getURIPropertyIfExists(properties, "deleteditems", URN_SCHEMAS_HTTPMAIL);
            sentitemsUrl = getURIPropertyIfExists(properties, "sentitems", URN_SCHEMAS_HTTPMAIL);
            sendmsgUrl = getURIPropertyIfExists(properties, "sendmsg", URN_SCHEMAS_HTTPMAIL);
            draftsUrl = getURIPropertyIfExists(properties, "drafts", URN_SCHEMAS_HTTPMAIL);
            calendarUrl = getURIPropertyIfExists(properties, "calendar", URN_SCHEMAS_HTTPMAIL);
            contactsUrl = getURIPropertyIfExists(properties, "contacts", URN_SCHEMAS_HTTPMAIL);

            // default public folder path
            publicFolderUrl = "/public";

            // check public folder access
            try {
                if (inboxUrl != null) {
                    // try to build full public URI from inboxUrl
                    URI publicUri = new URI(inboxUrl, false);
                    publicUri.setPath("/public");
                    publicFolderUrl = publicUri.getURI();
                }
                PropFindMethod propFindMethod = new PropFindMethod(publicFolderUrl, CONTENT_TAG, 0);
                try {
                    DavGatewayHttpClientFacade.executeMethod(httpClient, propFindMethod);
                } catch (IOException e) {
                    // workaround for NTLM authentication only on /public
                    if (!DavGatewayHttpClientFacade.hasNTLM(httpClient)) {
                        DavGatewayHttpClientFacade.addNTLM(httpClient);
                        DavGatewayHttpClientFacade.executeMethod(httpClient, propFindMethod);
                    }
                }
                // update public folder URI
                publicFolderUrl = propFindMethod.getURI().getURI();
            } catch (IOException e) {
                LOGGER.warn("Public folders not available: " + (e.getMessage() == null ? e : e.getMessage()));
                publicFolderUrl = "/public";
            }

            LOGGER.debug("Inbox URL : " + inboxUrl +
                    " Trash URL : " + deleteditemsUrl +
                    " Sent URL : " + sentitemsUrl +
                    " Send URL : " + sendmsgUrl +
                    " Drafts URL : " + draftsUrl +
                    " Calendar URL : " + calendarUrl +
                    " Contacts URL : " + contactsUrl +
                    " Public folder URL : " + publicFolderUrl
            );
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            throw new DavMailAuthenticationException("EXCEPTION_UNABLE_TO_GET_MAIL_FOLDER", mailPath);
        }
    }

    protected Folder buildFolder(MultiStatusResponse entity) throws IOException {
        String href = URIUtil.decode(entity.getHref());
        Folder folder = new Folder();
        DavPropertySet properties = entity.getProperties(HttpStatus.SC_OK);
        folder.contentClass = getPropertyIfExists(properties, "contentclass", DAV);
        folder.hasChildren = "1".equals(getPropertyIfExists(properties, "hassubs", DAV));
        folder.noInferiors = "1".equals(getPropertyIfExists(properties, "nosubs", DAV));
        folder.unreadCount = getIntPropertyIfExists(properties, "unreadcount", URN_SCHEMAS_HTTPMAIL);
        folder.ctag = getPropertyIfExists(properties, "contenttag", Namespace.getNamespace("http://schemas.microsoft.com/repl/"));
        folder.etag = getPropertyIfExists(properties, "resourcetag", Namespace.getNamespace("http://schemas.microsoft.com/repl/"));

        // replace well known folder names
        if (href.startsWith(inboxUrl)) {
            folder.folderPath = href.replaceFirst(inboxUrl, "INBOX");
        } else if (href.startsWith(sentitemsUrl)) {
            folder.folderPath = href.replaceFirst(sentitemsUrl, "Sent");
        } else if (href.startsWith(draftsUrl)) {
            folder.folderPath = href.replaceFirst(draftsUrl, "Drafts");
        } else if (href.startsWith(deleteditemsUrl)) {
            folder.folderPath = href.replaceFirst(deleteditemsUrl, "Trash");
        } else {
            int index = href.indexOf(mailPath.substring(0, mailPath.length() - 1));
            if (index >= 0) {
                if (index + mailPath.length() > href.length()) {
                    folder.folderPath = "";
                } else {
                    folder.folderPath = href.substring(index + mailPath.length());
                }
            } else {
                try {
                    URI folderURI = new URI(href, false);
                    folder.folderPath = folderURI.getPath();
                } catch (URIException e) {
                    throw new DavMailException("EXCEPTION_INVALID_FOLDER_URL", href);
                }
            }
        }
        if (folder.folderPath.endsWith("/")) {
            folder.folderPath = folder.folderPath.substring(0, folder.folderPath.length() - 1);
        }
        return folder;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Folder getFolder(String folderName) throws IOException {
        MultiStatusResponse[] responses = DavGatewayHttpClientFacade.executePropFindMethod(
                httpClient, URIUtil.encodePath(getFolderPath(folderName)), 0, FOLDER_PROPERTIES);
        Folder folder = null;
        if (responses.length > 0) {
            folder = buildFolder(responses[0]);
            folder.folderName = folderName;
        }
        return folder;
    }

    /**
     * @inheritDoc
     */
    @Override
    public List<Folder> getSubFolders(String folderName, String filter, boolean recursive) throws IOException {
        String mode = recursive ? "DEEP" : "SHALLOW";
        List<Folder> folders = new ArrayList<Folder>();
        StringBuilder searchRequest = new StringBuilder();
        searchRequest.append("Select \"DAV:nosubs\", \"DAV:hassubs\"," +
                "\"urn:schemas:httpmail:unreadcount\" FROM Scope('").append(mode).append(" TRAVERSAL OF \"").append(getFolderPath(folderName)).append("\"')\n" +
                " WHERE \"DAV:ishidden\" = False AND \"DAV:isfolder\" = True \n");
        if (filter != null && filter.length() > 0) {
            searchRequest.append("                      AND ").append(filter);
        }
        MultiStatusResponse[] responses = DavGatewayHttpClientFacade.executeSearchMethod(
                httpClient, URIUtil.encodePath(getFolderPath(folderName)), searchRequest.toString());

        for (MultiStatusResponse response : responses) {
            folders.add(buildFolder(response));
        }
        return folders;
    }
    
}
