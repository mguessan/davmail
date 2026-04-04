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

import davmail.exception.HttpConflictException;
import davmail.exception.HttpForbiddenException;
import davmail.util.IOUtil;
import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Test ExchangeSession contact features.
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestExchangeSessionContact extends AbstractExchangeSessionTestCase {

    final static String FOLDER_PATH = "contacts/testcontactfolder";
    static String itemName = null;

    protected ExchangeSession.Contact getCurrentContact() throws IOException {
        if (itemName != null) {
            return (ExchangeSession.Contact) session.getItem(FOLDER_PATH, itemName);
        } else {
            List<ExchangeSession.Contact> contacts = session.searchContacts(FOLDER_PATH, ExchangeSession.CONTACT_ATTRIBUTES, null, 0);
            itemName = contacts.get(0).itemName;
            return contacts.get(0);
        }
    }

    public void testCreateFolder() throws IOException {
        // recreate an empty folder, does not work over graph
        try {
            session.deleteFolder(FOLDER_PATH);
        } catch (HttpForbiddenException e) {
            // empty folder over graph
            List<ExchangeSession.Contact> contacts = session.searchContacts(FOLDER_PATH, ExchangeSession.CONTACT_ATTRIBUTES, null, 0);
            if (contacts != null) {
                for (ExchangeSession.Contact contact : contacts) {
                    session.deleteItem(FOLDER_PATH, contact.itemName);
                }
            }
        }
        try {
            session.createContactFolder(FOLDER_PATH, null);
        } catch (HttpConflictException e) {
            // folder already exists on graph
        } catch (IOException e) {
            if (e.getMessage() == null || !e.getMessage().startsWith("ErrorFolderExists")) {
                throw e;
            }
        }
    }

    public void testCreateContact() throws IOException {
        itemName = UUID.randomUUID() + ".vcf";
        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("N", "sn", "givenName", "middlename", "personaltitle", "namesuffix");
        vCardWriter.appendProperty("FN", "common name");
        vCardWriter.appendProperty("NICKNAME", "nickname");

        vCardWriter.appendProperty("TEL;TYPE=cell", "mobile");
        vCardWriter.appendProperty("TEL;TYPE=work", "telephoneNumber");
        vCardWriter.appendProperty("TEL;TYPE=home,voice", "homePhone");
        vCardWriter.appendProperty("TEL;TYPE=fax", "facsimiletelephonenumber");
        vCardWriter.appendProperty("TEL;TYPE=pager", "pager");

        vCardWriter.appendProperty("ADR;TYPE=home", "homepostofficebox", null, "homeStreet", "homeCity", "homeState", "homePostalCode", "homeCountry");
        vCardWriter.appendProperty("ADR;TYPE=work", "postofficebox", "roomnumber", "street", "l", "st", "postalcode", "co");
        vCardWriter.appendProperty("ADR;TYPE=other", "otherpostofficebox", null, "otherstreet", "othercity", "otherstate", "otherpostalcode", "othercountry");

        vCardWriter.appendProperty("EMAIL;TYPE=work", "email1@local.net");
        vCardWriter.appendProperty("EMAIL;TYPE=home", "email2@local.net");
        vCardWriter.appendProperty("EMAIL;TYPE=other", "email3@local.net");

        vCardWriter.appendProperty("ORG", "o", "department");

        vCardWriter.appendProperty("URL;TYPE=work", "https://local.net");
        vCardWriter.appendProperty("TITLE", "title");
        vCardWriter.appendProperty("NOTE", "description");

        vCardWriter.appendProperty("CUSTOM1", "extensionattribute1");
        vCardWriter.appendProperty("CUSTOM2", "extensionattribute2");
        vCardWriter.appendProperty("CUSTOM3", "extensionattribute3");
        vCardWriter.appendProperty("CUSTOM4", "extensionattribute4");

        vCardWriter.appendProperty("ROLE", "profession");
        vCardWriter.appendProperty("X-AIM", "im");
        vCardWriter.appendProperty("BDAY", "20000102");
        vCardWriter.appendProperty("X-ANNIVERSARY", "20000102");
        vCardWriter.appendProperty("CATEGORIES", "keyword1,keyword2");

        vCardWriter.appendProperty("FBURL", "http://fburl");

        vCardWriter.appendProperty("X-ASSISTANT", "secretarycn");
        vCardWriter.appendProperty("X-MANAGER", "manager");
        vCardWriter.appendProperty("X-SPOUSE", "spousecn");

        vCardWriter.appendProperty("CLASS", "PRIVATE");

        // add a contact photo
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream fileInputStream = Files.newInputStream(Paths.get("src/data/anonymous.jpg"));
        IOUtil.write(fileInputStream, baos);
        vCardWriter.appendProperty("PHOTO;ENCODING=b;TYPE=JPEG", new String(Base64.encodeBase64(baos.toByteArray())));

        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), null, null);
        assertEquals(201, result.status);

    }

    /**
     * Asserts contact properties match expected values
     */
    public void testGetContact() throws IOException {
        testCreateContact();
        ExchangeSession.Contact contact = getCurrentContact();

        assertEquals("common name", contact.get("cn"));
        assertEquals("sn", contact.get("sn"));
        assertEquals("givenName", contact.get("givenName"));
        assertEquals("middlename", contact.get("middlename"));
        assertEquals("personaltitle", contact.get("personaltitle"));
        assertEquals("namesuffix", contact.get("namesuffix"));
        assertNotNull(contact.get("lastmodified"));
        assertEquals("nickname", contact.get("nickname"));

        assertEquals("mobile", contact.get("mobile"));
        assertEquals("telephoneNumber", contact.get("telephoneNumber"));
        assertEquals("homePhone", contact.get("homePhone"));
        assertEquals("facsimiletelephonenumber", contact.get("facsimiletelephonenumber"));
        assertEquals("pager", contact.get("pager"));

        assertEquals("homepostofficebox", contact.get("homepostofficebox"));
        assertEquals("homeStreet", contact.get("homeStreet"));
        assertEquals("homeCity", contact.get("homeCity"));
        assertEquals("homeState", contact.get("homeState"));
        assertEquals("homePostalCode", contact.get("homePostalCode"));
        assertEquals("homeCountry", contact.get("homeCountry"));

        assertEquals("postofficebox", contact.get("postofficebox"));
        assertEquals("roomnumber", contact.get("roomnumber"));
        assertEquals("street", contact.get("street"));
        assertEquals("l", contact.get("l"));
        assertEquals("st", contact.get("st"));
        assertEquals("postalcode", contact.get("postalcode"));
        assertEquals("co", contact.get("co"));

        assertEquals("email1@local.net", contact.get("smtpemail1"));
        assertEquals("email2@local.net", contact.get("smtpemail2"));
        assertEquals("email3@local.net", contact.get("smtpemail3"));

        assertEquals("o", contact.get("o"));
        assertEquals("department", contact.get("department"));

        assertEquals("https://local.net", contact.get("businesshomepage"));
        assertEquals("title", contact.get("title"));
        assertEquals("description", contact.get("description"));

        assertEquals("extensionattribute1", contact.get("extensionattribute1"));
        assertEquals("extensionattribute2", contact.get("extensionattribute2"));
        assertEquals("extensionattribute3", contact.get("extensionattribute3"));
        assertEquals("extensionattribute4", contact.get("extensionattribute4"));

        assertEquals("profession", contact.get("profession"));
        assertEquals("im", contact.get("im"));
        // this is VCARD 3, VCARD4 is different, see https://datatracker.ietf.org/doc/html/rfc6350#section-6.2.5
        assertEquals("2000-01-02", session.convertZuluDateToBday(contact.get("bday")));

        assertEquals("otherpostofficebox", contact.get("otherpostofficebox"));
        assertEquals("otherstreet", contact.get("otherstreet"));
        assertEquals("othercity", contact.get("othercity"));
        assertEquals("otherstate", contact.get("otherstate"));
        assertEquals("otherpostalcode", contact.get("otherpostalcode"));
        assertEquals("othercountry", contact.get("othercountry"));

        assertEquals("secretarycn", contact.get("secretarycn"));
        assertEquals("manager", contact.get("manager"));
        assertEquals("spousecn", contact.get("spousecn"));
        assertEquals("keyword1,keyword2", contact.get("keywords"));

        assertEquals("true", contact.get("private"));

        assertEquals("http://fburl", contact.get("fburl"));

        assertEquals("true", contact.get("haspicture"));
        assertNotNull(session.getContactPhoto(contact));
    }

    /**
     * Tests contact update, verifying attribute clearing
     */
    public void testUpdateContact() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();

        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();
        assertNull(contact.get("cn"));
        assertNull(contact.get("sn"));
        assertNull(contact.get("givenName"));
        assertNull(contact.get("middlename"));
        assertNull(contact.get("personaltitle"));
        assertNull(contact.get("namesuffix"));
        assertNotNull(contact.get("lastmodified"));
        assertNull(contact.get("nickname"));

        assertNull(contact.get("mobile"));
        assertNull(contact.get("telephoneNumber"));
        assertNull(contact.get("homePhone"));
        assertNull(contact.get("facsimiletelephonenumber"));
        assertNull(contact.get("pager"));

        assertNull(contact.get("homepostofficebox"));
        assertNull(contact.get("homeStreet"));
        assertNull(contact.get("homeCity"));
        assertNull(contact.get("homeState"));
        assertNull(contact.get("homePostalCode"));
        assertNull(contact.get("homeCountry"));

        assertNull(contact.get("postofficebox"));
        assertNull(contact.get("roomnumber"));
        assertNull(contact.get("street"));
        assertNull(contact.get("l"));
        assertNull(contact.get("st"));
        assertNull(contact.get("postalcode"));
        assertNull(contact.get("co"));

        assertNull(contact.get("email1"));
        assertNull(contact.get("email2"));
        assertNull(contact.get("email3"));

        assertNull(contact.get("o"));
        assertNull(contact.get("department"));

        assertNull(contact.get("businesshomepage"));
        assertNull(contact.get("title"));
        assertNull(contact.get("description"));

        assertNull(contact.get("extensionattribute1"));
        assertNull(contact.get("extensionattribute2"));
        assertNull(contact.get("extensionattribute3"));
        assertNull(contact.get("extensionattribute4"));

        assertNull(contact.get("profession"));
        assertNull(contact.get("im"));
        assertNull(contact.get("bday"));

        assertNull(contact.get("otherpostofficebox"));
        assertNull(contact.get("otherstreet"));
        assertNull(contact.get("othercity"));
        assertNull(contact.get("otherstate"));
        assertNull(contact.get("otherpostalcode"));
        assertNull(contact.get("othercountry"));

        assertNull(contact.get("secretarycn"));
        assertNull(contact.get("manager"));
        assertNull(contact.get("spousecn"));
        assertNull(contact.get("keywords"));

        assertEquals("false", contact.get("private"));

        assertTrue(contact.get("haspicture") == null || "false".equals(contact.get("haspicture")));

        assertNull(session.getContactPhoto(contact));
    }


    public void testUpdateEmail() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("EMAIL;TYPE=work", "email1.test@local.net");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();

        assertEquals("email1.test@local.net", contact.get("smtpemail1"));

    }

    public void testUpperCaseParamName() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("TEL;TYPE=CELL", "mobile");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();

        assertEquals("mobile", contact.get("mobile"));

    }

    public void testMultipleTypesParamName() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("TEL;TYPE=CELL;TYPE=pref", "another mobile");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = (ExchangeSession.Contact) session.getItem(FOLDER_PATH, itemName);

        assertEquals("another mobile", contact.get("mobile"));

    }

    public void testLowerCaseTypesParamName() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("TEL;type=HOME;type=pref", "5 68 99 3");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = (ExchangeSession.Contact) session.getItem(FOLDER_PATH, itemName);

        assertEquals("5 68 99 3", contact.get("homePhone"));

    }

    public void testKeyPrefix() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("ITEM1.TEL;TYPE=CELL;TYPE=pref", "mobile with prefix");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = (ExchangeSession.Contact) session.getItem(FOLDER_PATH, itemName);

        assertEquals("mobile with prefix", contact.get("mobile"));

    }

    public void testIphonePersonalHomePage() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("ITEM1.URL", "https://www.myhomepage.org");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = (ExchangeSession.Contact) session.getItem(FOLDER_PATH, itemName);

        assertEquals("https://www.myhomepage.org", contact.get("personalHomePage"));

    }


    public void testIphoneEncodedCategories() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("CATEGORIES", "rouge,vert");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();

        assertEquals("vert,rouge", contact.get("keywords"));

    }

    public void testSemiColonInCompoundValue() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();
        String itemBody = "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "item1.ADR;type=WORK;type=pref:;;line1\\nline 2 \\; with semicolon;;;;\n" +
                "END:VCARD";

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, itemBody, contact.etag, null);
        assertEquals(200, result.status);
    }

    public void testIphoneEncodedComma() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("ITEM1.TEL;TYPE=CELL;TYPE=pref", "mobile\\, with comma");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();

        assertEquals("mobile, with comma", contact.get("mobile"));

    }

    public void testAmpersAndValue() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("FN", "common & name");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();

        assertEquals("common & name", contact.get("cn"));

    }

    public void testDateValue() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("BDAY", "2000-01-02");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();

        assertEquals("2000-01-02", session.convertZuluDateToBday(contact.get("bday")));
        System.out.println(contact.getBody());
    }

    public void testAnniversary() throws IOException {
        ExchangeSession.Contact contact = getCurrentContact();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("X-ANNIVERSARY", "2000-01-02");
        vCardWriter.endCard();

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), contact.etag, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();

        assertEquals("20000102T000000Z", contact.get("anniversary"));
        System.out.println(contact.getBody());
    }

    public void testSpecialUrlCharacters() throws IOException {
        testCreateFolder();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("N", "sn", "givenName", "middlename", "personaltitle", "namesuffix");
        vCardWriter.appendProperty("FN", "common name");
        vCardWriter.endCard();

        itemName = "test {<:&'>} \"accentué.vcf";

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), null, null);
        assertEquals(201, result.status);

        ExchangeSession.Contact contact = getCurrentContact();

        assertEquals("common name", contact.get("cn"));
    }

    public void testSpecialUrlCharacters3F() throws IOException {
        testCreateFolder();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("N", "sn", "givenName", "middlename", "personaltitle", "namesuffix");
        vCardWriter.appendProperty("FN", "common name");
        vCardWriter.endCard();

        itemName = "test ?.vcf";

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), null, null);
        assertEquals(201, result.status);

        ExchangeSession.Contact contact = getCurrentContact();

        assertEquals("common name", contact.get("cn"));
    }

    public void testPagingSearchContacts() throws IOException {
        int maxCount = 0;
        List<ExchangeSession.Contact> contacts = session.searchContacts(ExchangeSession.CONTACTS, ExchangeSession.CONTACT_ATTRIBUTES, null, maxCount);
        int folderSize = contacts.size();
        assertEquals(20, session.searchContacts(ExchangeSession.CONTACTS, ExchangeSession.CONTACT_ATTRIBUTES, null, 20).size());
        assertEquals(folderSize, session.searchContacts(ExchangeSession.CONTACTS, ExchangeSession.CONTACT_ATTRIBUTES, null, folderSize + 1).size());
    }

    public void testHashInName() throws IOException {
        testCreateFolder();

        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.startCard();
        vCardWriter.appendProperty("N", "sn", "givenName", "middlename", "personaltitle", "namesuffix");
        vCardWriter.appendProperty("FN", "common name");
        vCardWriter.endCard();

        itemName = "Capital 7654#.vcf";

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, vCardWriter.toString(), null, null);
        assertEquals(201, result.status);

        ExchangeSession.Contact contact = getCurrentContact();

        assertEquals("common name", contact.get("cn"));
    }


    public void testEmptyEmail() throws IOException {
        testCreateFolder();

        String itemBody = "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "UID:8b75b1a40f2c4e3a8cbdc86f26d4a497\n" +
                "N:name;common;;;\n" +
                "FN:common name\n" +
                "EMAIL;TYPE=WORK:\n" +
                "EMAIL;TYPE=HOME:email@company.com\n" +
                "END:VCARD";
        itemName = UUID.randomUUID() + ".vcf";

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, itemBody, null, null);
        assertEquals(201, result.status);

        ExchangeSession.Contact contact = getCurrentContact();

        assertNull(contact.get("smtpemail1"));

        result = session.createOrUpdateContact(FOLDER_PATH, itemName, itemBody, null, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();
        assertNull(contact.get("smtpemail1"));
    }

    public void testRemoveEmail() throws IOException {
        testCreateContact();

        ExchangeSession.Contact contact = getCurrentContact();

        assertNotNull(contact.get("smtpemail1"));
        assertNotNull(contact.get("smtpemail2"));

        String itemBody = "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "UID:8b75b1a40f2c4e3a8cbdc86f26d4a497\n" +
                "N:name;common;;;\n" +
                "FN:common name\n" +
                "END:VCARD";

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, itemBody, null, null);
        assertEquals(200, result.status);

        contact = getCurrentContact();

        assertNull(contact.get("smtpemail1"));
        assertNull(contact.get("smtpemail2"));
    }

    public void testProtectedComma() throws IOException {
        String itemBody = "BEGIN:VCARD\n" +
                "ADR;TYPE=WORK:;;via 25 aprile\\, 25;Lallio;BG;24048;Italia\n" +
                "END:VCARD";
        VObject vcard = new VObject(new ICSBufferedReader(new StringReader(itemBody)));
        System.out.println(vcard);
        VProperty property = new VProperty("ADR;TYPE=WORK:;;via 25 aprile\\, 25;Lallio;BG;24048;Italia");
        assertEquals("via 25 aprile, 25", property.getValues().get(2));
    }

    /* Huge contact folder creation
    public void testJohnDoes() throws IOException {
        testCreateFolder();

        for (int i = 0;i<10000;i++) {
            String itemBody = "BEGIN:VCARD\n" +
                    "VERSION:4.0\n" +
                    "EMAIL:john.doe"+i+"@acme.com\n" +
                    "FN:John Doe"+i+"\n" +
                    "N:Doe"+i+";John;;;\n" +
                    "TEL;TYPE=HOME:+1-234-56789\n" +
                    "UID:5516ecf5-6ee0-4d60-b5e8-a654c7447f0a\n" +
                    "END:VCARD";
            itemName = UUID.randomUUID().toString() + ".vcf";

            ExchangeSession.ItemResult result = session.createOrUpdateContact(folderPath, itemName, itemBody, null, null);
            assertEquals(201, result.status);
        }
        //result = session.createOrUpdateContact(folderPath, itemName, itemBody, null, null);
        //assertEquals(201, result.status);
    }*/

    /**
     */
    public void testEmptyEmail2() throws IOException {
        testCreateFolder();

        String itemBody = "BEGIN:VCARD\n" +
                "VERSION:3.0\n" +
                "UID:sd9327nnob97w02a36zoo9adf9vt692pr1hq\n" +
                "FN:First Last\n" +
                "N:Last;First;;;\n" +
                "REV:20161222T092209Z\n" +
                "TEL;TYPE=cell:+47 00000000\n" +
                "END:VCARD";
        itemName = UUID.randomUUID() + ".vcf";

        ExchangeSession.ItemResult result = session.createOrUpdateContact(FOLDER_PATH, itemName, itemBody, null, null);
        assertEquals(201, result.status);
    }

    public void testGetAllContacts() throws IOException {
        //session.getAllContacts("contacts");
        List<ExchangeSession.Contact> contacts = session.searchContacts("contacts", ExchangeSession.CONTACT_ATTRIBUTES, session.isEqualTo("outlookmessageclass", "IPM.Contact"), 0);
        //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
        for (ExchangeSession.Contact contact : contacts) {
            ExchangeSession.Item item = session.getItem("contacts", contact.getName());
            System.out.println((item).getBody());
        }
    }

    public void testGetAllDistributionLists() throws IOException {
        List<ExchangeSession.Contact> contacts = session.searchContacts("contacts", ExchangeSession.CONTACT_ATTRIBUTES, session.isEqualTo("outlookmessageclass", "IPM.DistList"), 0);
        //Settings.setLoggingLevel("httpclient.wire", Level.DEBUG);
        for (ExchangeSession.Contact contact : contacts) {
            ExchangeSession.Item item = session.getItem("contacts", contact.getName());
            System.out.println((item).getBody());
        }
    }

    public void testMultilineProperty() {
        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.appendProperty("NOTE", "multi line \r\n with crlf");
        // should drop CR and convert LF to \\n
        assertEquals("NOTE:multi line \\n with crlf\r\n", vCardWriter.toString());
    }

    public void testMultiValueMultilineProperty() {
        VCardWriter vCardWriter = new VCardWriter();
        vCardWriter.appendProperty("ADR", "value", "multi line \r\n with crlf");
        // should drop CR and convert LF to \\n
        assertEquals("ADR:value;multi line \\n with crlf\r\n", vCardWriter.toString());
    }

    public void testSearchContact() throws IOException {
        // reset folder and create full contact
        testCreateFolder();
        testCreateContact();

        ExchangeSession.Contact contact = getCurrentContact();
        System.out.println("Name: "+contact.getName()); // name is id + ".vcf"
        System.out.println("Uid: "+contact.getUid()); // getUid() returns id, id is not searchable
        System.out.println("Href: "+contact.getHref()); // full path followed by name
        System.out.println("Actual uid: "+contact.get("uid")); // binary field PR_RECORD_KEY, searchable

        assertEquals(1, session.searchContacts(FOLDER_PATH, ExchangeSession.CONTACT_ATTRIBUTES, session.isEqualTo("uid", contact.get("uid")), 0).size());

        //search by email
        assertEquals(1, session.searchContacts(FOLDER_PATH, ExchangeSession.CONTACT_ATTRIBUTES, session.contains("smtpemail1", "email1"), 0).size());
        assertEquals(1, session.searchContacts(FOLDER_PATH, ExchangeSession.CONTACT_ATTRIBUTES, session.contains("smtpemail2", "email2"), 0).size());
        assertEquals(1, session.searchContacts(FOLDER_PATH, ExchangeSession.CONTACT_ATTRIBUTES, session.contains("smtpemail3", "email3"), 0).size());

        assertEquals(1, session.searchContacts(FOLDER_PATH, ExchangeSession.CONTACT_ATTRIBUTES, session.contains("smtpemail1", "@local.net"), 0).size());
        assertEquals(1, session.searchContacts(FOLDER_PATH, ExchangeSession.CONTACT_ATTRIBUTES, session.startsWith("smtpemail1", "email1"), 0).size());
    }

}
