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

import davmail.exchange.XMLStreamUtil;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.Writer;

/**
 * GetUserAvailability method.
 */
public class GetUserAvailabilityMethod extends EWSMethod {
    protected String attendee;
    protected String start;
    protected String end;
    protected String mergedFreeBusy;
    protected int interval;

    /**
     * Build EWS method
     *
     * @param attendee attendee email address
     * @param start    start date in Exchange zulu format
     * @param end      end date in Exchange zulu format
     * @param interval freebusy interval in minutes
     */
    public GetUserAvailabilityMethod(String attendee, String start, String end, int interval) {
        super("FreeBusy", "GetUserAvailabilityRequest");
        this.attendee = attendee;
        this.start = start;
        this.end = end;
        this.interval = interval;
    }

    @Override
    protected void writeSoapBody(Writer writer) throws IOException {
        // write UTC timezone
        writer.write("<t:TimeZone>" +
                "<t:Bias>0</t:Bias>" +
                "<t:StandardTime>" +
                "<t:Bias>0</t:Bias>" +
                "<t:Time>00:00:00</t:Time>" +
                "<t:DayOrder>1</t:DayOrder>" +
                "<t:Month>1</t:Month>" +
                "<t:DayOfWeek>Sunday</t:DayOfWeek>" +
                "</t:StandardTime>" +
                "<t:DaylightTime>" +
                "<t:Bias>0</t:Bias>" +
                "<t:Time>00:00:00</t:Time>" +
                "<t:DayOrder>1</t:DayOrder>" +
                "<t:Month>1</t:Month>" +
                "<t:DayOfWeek>Sunday</t:DayOfWeek>" +
                "</t:DaylightTime>" +
                "</t:TimeZone>");
        // write attendee address
        writer.write("<m:MailboxDataArray>" +
                "<t:MailboxData>" +
                "<t:Email>" +
                "<t:Address>");
        writer.write(attendee);
        writer.write("</t:Address>" +
                "</t:Email>" +
                "<t:AttendeeType>Required</t:AttendeeType>" +
                "</t:MailboxData>" +
                "</m:MailboxDataArray>");
        // freebusy range
        writer.write("<t:FreeBusyViewOptions>" +
                "<t:TimeWindow>" +
                "<t:StartTime>");
        writer.write(start);
        writer.write("</t:StartTime>" +
                "<t:EndTime>");
        writer.write(end);
        writer.write("</t:EndTime>" +
                "</t:TimeWindow>" +
                "<t:MergedFreeBusyIntervalInMinutes>60</t:MergedFreeBusyIntervalInMinutes>" +
                "<t:RequestedView>MergedOnly</t:RequestedView>" +
                "</t:FreeBusyViewOptions>");
    }

    @Override
    protected void handleCustom(XMLStreamReader reader) throws XMLStreamException {
        if (XMLStreamUtil.isStartTag(reader, "MergedFreeBusy")) {
            this.mergedFreeBusy = reader.getElementText();
        }
    }

    public String getMergedFreeBusy() {
        return mergedFreeBusy;
    }
}
