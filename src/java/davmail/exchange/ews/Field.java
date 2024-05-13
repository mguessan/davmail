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
package davmail.exchange.ews;

import java.util.HashMap;
import java.util.Map;

/**
 * EWS MAPI fields;
 */
public final class Field {
    private static final Map<String, FieldURI> FIELD_MAP = new HashMap<>();

    private Field() {
    }

    static {
        // items
        FIELD_MAP.put("etag", new ExtendedFieldURI(0x3008, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("displayname", new ExtendedFieldURI(0x3001, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("urlcompname", new ExtendedFieldURI(0x10f3, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("lastmodified", new ExtendedFieldURI(0x3008, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("created", new ExtendedFieldURI(0x3007, ExtendedFieldURI.PropertyType.SystemTime));

        // folder
        FIELD_MAP.put("ctag", new ExtendedFieldURI(0x670a, ExtendedFieldURI.PropertyType.SystemTime)); // PR_LOCAL_COMMIT_TIME_MAX
        FIELD_MAP.put("count", new ExtendedFieldURI(0x3602, ExtendedFieldURI.PropertyType.Integer)); // PR_CONTENT_COUNT
        FIELD_MAP.put("unread", new ExtendedFieldURI(0x3603, ExtendedFieldURI.PropertyType.Integer)); // PR_CONTENT_UNREAD

        FIELD_MAP.put("hassubs", new ExtendedFieldURI(0x360a, ExtendedFieldURI.PropertyType.Boolean)); // PR_SUBFOLDERS
        FIELD_MAP.put("folderDisplayName", new UnindexedFieldURI("folder:DisplayName"));

        FIELD_MAP.put("uidNext", new ExtendedFieldURI(0x6751, ExtendedFieldURI.PropertyType.Integer)); // PR_ARTICLE_NUM_NEXT
        FIELD_MAP.put("highestUid", new ExtendedFieldURI(0x6752, ExtendedFieldURI.PropertyType.Integer)); // PR_IMAP_LAST_ARTICLE_ID

        FIELD_MAP.put("permanenturl", new ExtendedFieldURI(0x670E, ExtendedFieldURI.PropertyType.String)); //PR_FLAT_URL_NAME
        FIELD_MAP.put("instancetype", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.PublicStrings, "urn:schemas:calendar:instancetype", ExtendedFieldURI.PropertyType.Integer));
        //FIELD_MAP.put("dtstart", new ExtendedFieldURI(0x10C3, ExtendedFieldURI.PropertyType.SystemTime));
        //FIELD_MAP.put("dtend", new ExtendedFieldURI(0x10C4, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("dtstart", new UnindexedFieldURI("calendar:Start"));
        FIELD_MAP.put("dtend", new UnindexedFieldURI("calendar:End"));

        FIELD_MAP.put("originalstart", new UnindexedFieldURI("calendar:OriginalStart"));

        FIELD_MAP.put("mimeContent", new UnindexedFieldURI("item:MimeContent"));

        // use PR_RECORD_KEY as unique key
        FIELD_MAP.put("uid", new ExtendedFieldURI(0x0FF9, ExtendedFieldURI.PropertyType.Binary));
        FIELD_MAP.put("messageFlags", new ExtendedFieldURI(0x0e07, ExtendedFieldURI.PropertyType.Integer));//PR_MESSAGE_FLAGS
        FIELD_MAP.put("imapUid", new ExtendedFieldURI(0x0e23, ExtendedFieldURI.PropertyType.Integer));
        FIELD_MAP.put("flagStatus", new ExtendedFieldURI(0x1090, ExtendedFieldURI.PropertyType.Integer));
        FIELD_MAP.put("lastVerbExecuted", new ExtendedFieldURI(0x1081, ExtendedFieldURI.PropertyType.Integer));
        FIELD_MAP.put("read", new UnindexedFieldURI("message:IsRead"));
        FIELD_MAP.put("messageSize", new ExtendedFieldURI(0x0e08, ExtendedFieldURI.PropertyType.Integer));
        FIELD_MAP.put("date", new ExtendedFieldURI(0x0e06, ExtendedFieldURI.PropertyType.SystemTime));
        // always empty on Exchange 2007
        //FIELD_MAP.put("messageSize", new ExtendedFieldURI(0x6746, ExtendedFieldURI.PropertyType.Integer)); // PR_MIME_SIZE
        //FIELD_MAP.put("date", new ExtendedFieldURI(0x65f5, ExtendedFieldURI.PropertyType.SystemTime)); // PR_IMAP_INTERNAL_DATE
        FIELD_MAP.put("deleted", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Common, 0x8570, ExtendedFieldURI.PropertyType.Integer)); // PidLidImapDeleted
        FIELD_MAP.put("junk", new ExtendedFieldURI(0x1083, ExtendedFieldURI.PropertyType.Integer));

        FIELD_MAP.put("iconIndex", new ExtendedFieldURI(0x1080, ExtendedFieldURI.PropertyType.Integer));// PR_ICON_INDEX
        FIELD_MAP.put("datereceived", new ExtendedFieldURI(0x0e06, ExtendedFieldURI.PropertyType.SystemTime));// PR_MESSAGE_DELIVERY_TIME

        FIELD_MAP.put("msgfrom", new UnindexedFieldURI("message:From"));
        FIELD_MAP.put("msgto", new UnindexedFieldURI("message:ToRecipients"));
        FIELD_MAP.put("msgcc", new UnindexedFieldURI("message:CcRecipients"));
        FIELD_MAP.put("msgbcc", new UnindexedFieldURI("message:BccRecipients"));

        FIELD_MAP.put("from", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.InternetHeaders, "from"));
        FIELD_MAP.put("to", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.InternetHeaders, "to"));
        FIELD_MAP.put("displayto", new UnindexedFieldURI("item:DisplayTo"));
        FIELD_MAP.put("cc", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.InternetHeaders, "cc"));
        FIELD_MAP.put("displaycc", new UnindexedFieldURI("item:DisplayCc"));
        FIELD_MAP.put("bcc", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.InternetHeaders, "bcc"));

        FIELD_MAP.put("message-id", new UnindexedFieldURI("message:InternetMessageId"));

        FIELD_MAP.put("messageheaders", new ExtendedFieldURI(0x007D, ExtendedFieldURI.PropertyType.String)); // PR_TRANSPORT_MESSAGE_HEADERS

        FIELD_MAP.put("contentclass", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.InternetHeaders, "content-class"));


        FIELD_MAP.put("body", new UnindexedFieldURI("item:Body"));
        FIELD_MAP.put("textbody", new UnindexedFieldURI("item:TextBody"));

        // folder
        FIELD_MAP.put("folderclass", new ExtendedFieldURI(0x3613, ExtendedFieldURI.PropertyType.String));

        // contact

        FIELD_MAP.put("outlookmessageclass", new ExtendedFieldURI(0x001A, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("subject", new ExtendedFieldURI(0x0037, ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("middlename", new ExtendedFieldURI(0x3A44, ExtendedFieldURI.PropertyType.String));
        //FIELD_MAP.put("fileas", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.PublicStrings, "urn:schemas:contacts:fileas"));
        FIELD_MAP.put("fileas", new UnindexedFieldURI("contacts:FileAs"));

        FIELD_MAP.put("members", new UnindexedFieldURI("distributionlist:Members"));

        FIELD_MAP.put("homepostaladdress", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x801A, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("otherpostaladdress", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x801C, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("mailingaddressid", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8022, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("workaddress", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x801B, ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("alternaterecipient", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.PublicStrings, "urn:schemas:contacts:alternaterecipient"));

        FIELD_MAP.put("extensionattribute1", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x804F, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("extensionattribute2", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8050, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("extensionattribute3", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8051, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("extensionattribute4", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8052, ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("bday", new ExtendedFieldURI(0x3A42, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("anniversary", new ExtendedFieldURI(0x3A41, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("businesshomepage", new ExtendedFieldURI(0x3A51, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("personalHomePage", new ExtendedFieldURI(0x3A50, ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("cn", new ExtendedFieldURI(0x3001, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("co", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8049, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("department", new ExtendedFieldURI(0x3A18, ExtendedFieldURI.PropertyType.String));

        /*
        FIELD_MAP.put("email1", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8083, ExtendedFieldURI.PropertyType.String)); // Email1EmailAddress
        FIELD_MAP.put("email2", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8093, ExtendedFieldURI.PropertyType.String)); // Email2EmailAddress
        FIELD_MAP.put("email3", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x80A3, ExtendedFieldURI.PropertyType.String)); // Email3EmailAddress

        FIELD_MAP.put("smtpemail1", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8084, ExtendedFieldURI.PropertyType.String)); // Email1OriginalDisplayName
        FIELD_MAP.put("smtpemail2", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8094, ExtendedFieldURI.PropertyType.String)); // Email2OriginalDisplayName
        FIELD_MAP.put("smtpemail3", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x80A4, ExtendedFieldURI.PropertyType.String)); // Email3OriginalDisplayName

        FIELD_MAP.put("displayemail1", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8080, ExtendedFieldURI.PropertyType.String)); // Email1DisplayName
        FIELD_MAP.put("displayemail2", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8090, ExtendedFieldURI.PropertyType.String)); // Email2DisplayName
        FIELD_MAP.put("displayemail3", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x80A0, ExtendedFieldURI.PropertyType.String)); // Email3DisplayName
        */
        FIELD_MAP.put("smtpemail1", new IndexedFieldURI("contacts:EmailAddress", "EmailAddress1", "Contact", "EmailAddresses"));
        FIELD_MAP.put("smtpemail2", new IndexedFieldURI("contacts:EmailAddress", "EmailAddress2", "Contact", "EmailAddresses"));
        FIELD_MAP.put("smtpemail3", new IndexedFieldURI("contacts:EmailAddress", "EmailAddress3", "Contact", "EmailAddresses"));

        FIELD_MAP.put("facsimiletelephonenumber", new ExtendedFieldURI(0x3A24, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("givenName", new ExtendedFieldURI(0x3A06, ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("homepostofficebox", new ExtendedFieldURI(0x3A5E, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("homeCity", new ExtendedFieldURI(0x3A59, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("homeCountry", new ExtendedFieldURI(0x3A5A, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("homePhone", new ExtendedFieldURI(0x3A09, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("homePostalCode", new ExtendedFieldURI(0x3A5B, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("homeState", new ExtendedFieldURI(0x3A5C, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("homeStreet", new ExtendedFieldURI(0x3A5D, ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("l", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8046, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("manager", new ExtendedFieldURI(0x3A4E, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("mobile", new ExtendedFieldURI(0x3A1C, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("namesuffix", new ExtendedFieldURI(0x3A05, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("nickname", new ExtendedFieldURI(0x3A4F, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("o", new ExtendedFieldURI(0x3A16, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("pager", new ExtendedFieldURI(0x3A21, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("personaltitle", new ExtendedFieldURI(0x3A45, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("postalcode", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8048, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("postofficebox", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x804A, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("profession", new ExtendedFieldURI(0x3A46, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("roomnumber", new ExtendedFieldURI(0x3A19, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("secretarycn", new ExtendedFieldURI(0x3A30, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("sn", new ExtendedFieldURI(0x3A11, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("spousecn", new ExtendedFieldURI(0x3A48, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("st", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8047, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("street", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8045, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("telephoneNumber", new ExtendedFieldURI(0x3A08, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("title", new ExtendedFieldURI(0x3A17, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("description", new ExtendedFieldURI(0x1000, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("im", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8062, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("othermobile", new ExtendedFieldURI(0x3A1E, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("internationalisdnnumber", new ExtendedFieldURI(0x3A2D, ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("otherTelephone", new ExtendedFieldURI(0x3A21, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("homefax", new ExtendedFieldURI(0x3A25, ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("otherstreet", new ExtendedFieldURI(0x3A63, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("otherstate", new ExtendedFieldURI(0x3A62, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("otherpostofficebox", new ExtendedFieldURI(0x3A64, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("otherpostalcode", new ExtendedFieldURI(0x3A61, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("othercountry", new ExtendedFieldURI(0x3A60, ExtendedFieldURI.PropertyType.String));
        FIELD_MAP.put("othercity", new ExtendedFieldURI(0x3A5F, ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("gender", new ExtendedFieldURI(0x3A4D, ExtendedFieldURI.PropertyType.Short));

        FIELD_MAP.put("keywords", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.PublicStrings, "Keywords", ExtendedFieldURI.PropertyType.StringArray));

        FIELD_MAP.put("private", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Common, 0x8506, ExtendedFieldURI.PropertyType.Boolean));
        FIELD_MAP.put("sensitivity", new ExtendedFieldURI(0x0036, ExtendedFieldURI.PropertyType.Integer));

        // TODO: merge with sensitivity ?
        FIELD_MAP.put("itemsensitivity", new UnindexedFieldURI("item:Sensitivity"));

        FIELD_MAP.put("haspicture", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x8015, ExtendedFieldURI.PropertyType.Boolean));

        FIELD_MAP.put("fburl", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Address, 0x80D8, ExtendedFieldURI.PropertyType.String));

        // calendar
        FIELD_MAP.put("processed", new ExtendedFieldURI(0x65e8, ExtendedFieldURI.PropertyType.Boolean));

        FIELD_MAP.put("reminderset", new UnindexedFieldURI("item:ReminderIsSet"));
        FIELD_MAP.put("reminderminutesbeforestart", new UnindexedFieldURI("item:ReminderMinutesBeforeStart"));

        FIELD_MAP.put("ismeeting", new UnindexedFieldURI("calendar:IsMeeting"));
        FIELD_MAP.put("apptstateflags", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Appointment, 0x8217, ExtendedFieldURI.PropertyType.Integer)); // PidLidAppointmentStateFlags 1: Meeting, 2: Received, 4: Cancelled
        FIELD_MAP.put("appointmentstate", new UnindexedFieldURI("calendar:AppointmentState"));
        // isorganizer is Exchange 2013 and later only
        FIELD_MAP.put("isorganizer", new UnindexedFieldURI("calendar:IsOrganizer"));

        FIELD_MAP.put("calendaruid", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.PublicStrings, "urn:schemas:calendar:uid", ExtendedFieldURI.PropertyType.String));

        FIELD_MAP.put("meetingtimezone", new UnindexedFieldURI("calendar:MeetingTimeZone"));
        FIELD_MAP.put("starttimezone", new UnindexedFieldURI("calendar:StartTimeZone"));
        FIELD_MAP.put("endtimezone", new UnindexedFieldURI("calendar:EndTimeZone"));
        FIELD_MAP.put("busystatus", new UnindexedFieldURI("calendar:LegacyFreeBusyStatus"));

        FIELD_MAP.put("requiredattendees", new UnindexedFieldURI("calendar:RequiredAttendees"));
        FIELD_MAP.put("optionalattendees", new UnindexedFieldURI("calendar:OptionalAttendees"));
        FIELD_MAP.put("modifiedoccurrences", new UnindexedFieldURI("calendar:ModifiedOccurrences"));
        FIELD_MAP.put("deletedoccurrences", new UnindexedFieldURI("calendar:DeletedOccurrences"));

        FIELD_MAP.put("recurrence", new UnindexedFieldURI("calendar:Recurrence"));

        FIELD_MAP.put("isalldayevent", new UnindexedFieldURI("calendar:IsAllDayEvent"));
        FIELD_MAP.put("myresponsetype", new UnindexedFieldURI("calendar:MyResponseType"));

        // does not work with Office 365, see https://msdn.microsoft.com/en-us/library/office/bb204271(v=exchg.150).aspx
        //FIELD_MAP.put("isrecurring", new UnindexedFieldURI("calendar:IsRecurring"));
        FIELD_MAP.put("isrecurring", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Appointment, 0x8223, ExtendedFieldURI.PropertyType.Boolean)); // PidLidRecurring

        FIELD_MAP.put("calendaritemtype", new UnindexedFieldURI("calendar:CalendarItemType"));
        // https://msdn.microsoft.com/en-us/library/cc842017.aspx
        FIELD_MAP.put("recurringappointment", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Appointment, 0x8216, ExtendedFieldURI.PropertyType.Binary));
        FIELD_MAP.put("recurrencestart", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Appointment, 0x8235, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("recurrencetype", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Appointment, 0x8231, ExtendedFieldURI.PropertyType.Integer));

        FIELD_MAP.put("location", new UnindexedFieldURI("calendar:Location"));

        FIELD_MAP.put("xmozlastack", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.PublicStrings, "xmozlastack"));
        FIELD_MAP.put("xmozsnoozetime", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.PublicStrings, "xmozsnoozetime"));
        FIELD_MAP.put("xmozsendinvitations", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.PublicStrings, "xmozsendinvitations"));

        // task
        FIELD_MAP.put("importance", new UnindexedFieldURI("item:Importance"));
        FIELD_MAP.put("percentcomplete", new UnindexedFieldURI("task:PercentComplete"));
        FIELD_MAP.put("taskstatus", new UnindexedFieldURI("task:Status"));

        FIELD_MAP.put("startdate", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Task, 0x8104, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("duedate", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Task, 0x8105, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("datecompleted", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Task, 0x810F, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("iscomplete", new UnindexedFieldURI("task:IsComplete"));

        FIELD_MAP.put("commonstart", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Task, 0x8516, ExtendedFieldURI.PropertyType.SystemTime));
        FIELD_MAP.put("commonend", new ExtendedFieldURI(ExtendedFieldURI.DistinguishedPropertySetType.Task, 0x8517, ExtendedFieldURI.PropertyType.SystemTime));

        // attachments
        FIELD_MAP.put("attachments", new UnindexedFieldURI("item:Attachments"));

        // user certificate
        FIELD_MAP.put("msexchangecertificate", new UnindexedFieldURI("contacts:MSExchangeCertificate"));
        FIELD_MAP.put("usersmimecertificate", new UnindexedFieldURI("contacts:UserSMIMECertificate"));
    }

    /**
     * Get Field by alias.
     *
     * @param alias field alias
     * @return field
     */
    public static FieldURI get(String alias) {
        FieldURI field = FIELD_MAP.get(alias);
        if (field == null) {
            throw new IllegalArgumentException("Unknown field: " + alias);
        }
        return field;
    }

    /**
     * Create property update field
     *
     * @param alias property alias
     * @param value property value
     * @return field update
     */
    public static FieldUpdate createFieldUpdate(String alias, String value) {
        return new FieldUpdate(Field.get(alias), value);
    }

}
