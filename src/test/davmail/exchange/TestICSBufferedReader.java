package davmail.exchange;

import junit.framework.TestCase;

import java.io.StringReader;
import java.io.IOException;

/**
 * Test ICSBufferedReader
 */
public class TestICSBufferedReader extends TestCase {
    public void testSimpleRead() throws IOException {
        String value = "test\nmultiline\nstring";
        ICSBufferedReader reader = new ICSBufferedReader(new StringReader(value));
        assertEquals("test", reader.readLine());
        assertEquals("multiline", reader.readLine());
        assertEquals("string", reader.readLine());
        assertNull(reader.readLine());
    }

    public void testContinuationRead() throws IOException {
        String value = "test\nmultiline\n string";
        ICSBufferedReader reader = new ICSBufferedReader(new StringReader(value));
        assertEquals("test", reader.readLine());
        assertEquals("multilinestring", reader.readLine());
        assertNull(reader.readLine());
    }
}
