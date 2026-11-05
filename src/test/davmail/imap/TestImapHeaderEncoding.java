/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2026  DavMail contributors
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
package davmail.imap;

import davmail.exchange.ExchangeSession;
import davmail.exchange.ews.EwsExchangeSession;
import davmail.http.HttpClientAdapter;
import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Offline regression coverage of actual IMAP FETCH header serialization. */
public class TestImapHeaderEncoding extends TestCase {
    public void testRawUtf8Subject() throws Exception {
        assertAllPaths("Subject: Проверка кириллицы".getBytes(StandardCharsets.UTF_8));
    }

    public void testAsciiSubject() throws Exception {
        assertAllPaths("Subject: Plain ASCII".getBytes(StandardCharsets.US_ASCII));
    }

    public void testEncodedWordBase64Subject() throws Exception {
        assertAllPaths("Subject: =?UTF-8?B?0J/RgNC40LLQtdGC?=".getBytes(StandardCharsets.US_ASCII));
    }

    public void testEncodedWordQuotedPrintableSubject() throws Exception {
        assertAllPaths("Subject: =?UTF-8?Q?=D0=9F=D1=80=D0=B8=D0=B2=D0=B5=D1=82?="
                .getBytes(StandardCharsets.US_ASCII));
    }

    public void testLegacyEightBitSubject() throws Exception {
        assertAllPaths("Subject: Caf\u00e9".getBytes(StandardCharsets.ISO_8859_1));
    }

    public void testFoldedSubject() throws Exception {
        assertAllPaths("Subject: Первая строка\r\n\tвторая строка".getBytes(StandardCharsets.UTF_8));
    }

    public void testRawUtf8From() throws Exception {
        assertAllPaths(("Subject: Plain ASCII\r\nFrom: Проверка кириллицы <fixture@example.invalid>")
                .getBytes(StandardCharsets.UTF_8), "SUBJECT FROM");
    }

    private void assertAllPaths(byte[] selectedHeader) throws Exception {
        assertAllPaths(selectedHeader, "SUBJECT");
    }

    private void assertAllPaths(byte[] selectedHeader, String requestedHeaders) throws Exception {
        // 0: optimized headers; 1: MIME already loaded; 2: missing optimized
        // headers forces the production full-message loading fallback.
        for (int path = 0; path < 3; path++) {
            FixtureSession session = new FixtureSession();
            FixtureSession.FixtureMessage message = session.new FixtureMessage(selectedHeader, path);
            if (path == 1) {
                message.loadMimeMessage();
            }
            MemorySocket socket = new MemorySocket();
            ImapConnection connection = new ImapConnection(socket);
            Method fetch = ImapConnection.class.getDeclaredMethod("handleFetch",
                    ExchangeSession.Message.class, int.class, String.class);
            fetch.setAccessible(true);
            try {
                fetch.invoke(connection, message, 1, "BODY.PEEK[HEADER.FIELDS (" + requestedHeaders + ")]");
            } catch (InvocationTargetException e) {
                throw new AssertionError("FETCH failed for path " + path, e.getCause());
            }

            byte[] wire = socket.output.toByteArray();
            String response = new String(wire, StandardCharsets.ISO_8859_1);
            int literalStart = response.indexOf('{');
            int literalEnd = response.indexOf("}\r\n", literalStart);
            assertTrue("missing literal for path " + path + ": " + response, literalStart >= 0 && literalEnd >= 0);
            int count = Integer.parseInt(response.substring(literalStart + 1, literalEnd));
            byte[] expected = concatenate(selectedHeader, new byte[]{13, 10});
            assertEquals("literal size for path " + path, expected.length, count);
            int payloadStart = literalEnd + 3;
            assertTrue("literal octets changed for path " + path,
                    Arrays.equals(expected, Arrays.copyOfRange(wire, payloadStart, payloadStart + count)));
            assertEquals("unexpected bytes after literal for path " + path,
                    ")\r\n", response.substring(payloadStart + count));
            assertEquals("full MIME download count for path " + path, path == 0 ? 0 : 1, session.contentLoads);
        }
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /** Construction calls an overridden no-op initializer, never Exchange. */
    private static class FixtureSession extends EwsExchangeSession {
        private int contentLoads;

        FixtureSession() throws Exception {
            super((HttpClientAdapter) null, "fixture@example.invalid");
        }

        @Override
        public void buildSessionInfo(URI uri) {
            // No HTTP client or credentials are needed by this fixture.
        }

        @Override
        protected byte[] getContent(ExchangeSession.Message message) {
            contentLoads++;
            return ((FixtureMessage) message).content;
        }

        class FixtureMessage extends ExchangeSession.Message {
            private final byte[] content;
            private final int path;

            FixtureMessage(byte[] selectedHeader, int path) {
                this.path = path;
                content = concatenate(selectedHeader,
                        "\r\nMessage-ID: <fixture@example.invalid>\r\n\r\nBody\r\n"
                                .getBytes(StandardCharsets.US_ASCII));
                imapUid = 42;
                messageList = new ExchangeSession.MessageList();
            }

            @Override
            public String getPermanentId() {
                return "fixture";
            }

            @Override
            protected InputStream getMimeHeaders() {
                return path == 2 ? null : new ByteArrayInputStream(content);
            }
        }
    }

    /** Socket API backed entirely by memory; never binds or connects. */
    private static class MemorySocket extends Socket {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public InetAddress getInetAddress() {
            return InetAddress.getLoopbackAddress();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }
    }
}
