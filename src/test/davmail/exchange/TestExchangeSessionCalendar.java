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

import davmail.AbstractDavMailTestCase;
import davmail.Settings;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Test Exchange session calendar features .
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TestExchangeSessionCalendar extends AbstractDavMailTestCase {

    public void testGetVtimezone() {
        ExchangeSession.VTimezone timezone = session.getVTimezone();
        assertNotNull(timezone.timezoneId);
        assertNotNull(timezone.timezoneBody);
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
              ExchangeSession.VTimezone timezone = session.getVTimezone();
              if (timezone.timezoneId != null) {
                  properties.put(timezone.timezoneId.replaceAll("\\\\", ""), String.valueOf(i));
                  System.out.println(timezone.timezoneId + '=' + i);
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
        List<ExchangeSession.Event> events = session.getAllEvents("/users/" + session.getEmail() + "/calendar/test");
        for (ExchangeSession.Event event:events) {
            System.out.println(event.getBody());
        }
    }
    
}
