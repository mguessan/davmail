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

import davmail.exchange.ExchangeSession;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implement ExchangeSession based on Microsoft Graph
 */
public class GraphExchangeSession extends ExchangeSession {
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
        return null;
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
    public List<Folder> getSubFolders(String folderName, Condition condition, boolean recursive) throws IOException {
        return null;
    }

    @Override
    public void sendMessage(MimeMessage mimeMessage) throws IOException, MessagingException {

    }

    @Override
    protected Folder internalGetFolder(String folderName) throws IOException {
        return null;
    }

    @Override
    public int createFolder(String folderName, String folderClass, Map<String, String> properties) throws IOException {
        return 0;
    }

    @Override
    public int updateFolder(String folderName, Map<String, String> properties) throws IOException {
        return 0;
    }

    @Override
    public void deleteFolder(String folderName) throws IOException {

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
        return null;
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

    }
}
