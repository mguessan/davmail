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

import davmail.Settings;

import javax.mail.MessagingException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Test Exchange session calendar features .
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestExchangeSessionCalendar extends AbstractExchangeSessionTestCase {

    public void testGetVtimezone() {
        VObject timezone = session.getVTimezone();
        assertNotNull(timezone);
        assertNotNull(timezone.getPropertyValue("TZID"));
    }

    public void testDumpVtimezones() throws IOException {
          Properties properties = new Properties() {
              @Override
              public synchronized Enumeration<Object> keys() {
                  Enumeration keysEnumeration = super.keys();
                  TreeSet<String> sortedKeySet = new TreeSet<String>();
                  while (keysEnumeration.hasMoreElements()) {
                      sortedKeySet.add((String) keysEnumeration.nextElement());
                  }
                  final Iterator<String> sortedKeysIterator = sortedKeySet.iterator();
                  return new Enumeration<Object>() {

                      public boolean hasMoreElements() {
                          return sortedKeysIterator.hasNext();
                      }

                      public Object nextElement() {
                          return sortedKeysIterator.next();
                      }
                  };
              }

          };
          for (int i = 1; i < 100; i++) {
              Settings.setProperty("davmail.timezoneId", String.valueOf(i));
              VObject timezone = session.getVTimezone();
              if (timezone.getProperty("TZID") != null) {
                  properties.put(timezone.getPropertyValue("TZID").replaceAll("\\\\", ""), String.valueOf(i));
                  System.out.println(timezone.getPropertyValue("TZID") + '=' + i);
              }
              session.vTimezone = null;
          }
          FileOutputStream fileOutputStream = null;
          try {
              fileOutputStream = new FileOutputStream("timezoneids.properties");
              properties.store(fileOutputStream, "Timezone ids");
          } finally {
              if (fileOutputStream != null) {
                  try {
                      fileOutputStream.close();
                  } catch (IOException e) {
                      // ignore
                  }
              }
          }
      }

    public void testSearchCalendar() throws IOException {
        List<ExchangeSession.Event> events = null;
        try {
            events = session.getAllEvents("/users/" + session.getEmail() + "/calendar");
        for (ExchangeSession.Event event:events) {
            System.out.println(event.getBody());
        }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw e;
        }
    }

    public void testGetFreeBusyData() throws IOException, MessagingException {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.MONTH, 7);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date startDate = cal.getTime();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        Date endDate = cal.getTime();
        SimpleDateFormat formatter = ExchangeSession.getExchangeZuluDateFormat();
        // personal fbdata
        String fbdata = session.getFreeBusyData(session.getEmail(), formatter.format(startDate),
                formatter.format(endDate), 60);
        assertNotNull(fbdata);
        // other user data
        fbdata = session.getFreeBusyData(Settings.getProperty("davmail.to"), formatter.format(startDate),
                formatter.format(endDate), 60);
        assertNotNull(fbdata);
         // unknown user data
        fbdata = session.getFreeBusyData("unknown@company.org", formatter.format(startDate),
                formatter.format(endDate), 60);
        assertNull(fbdata);
    }

    
}

