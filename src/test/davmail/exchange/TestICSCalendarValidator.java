// Tests for davmail/src/java/davmail/exchange/ICSCalendarValidator.java

package davmail.exchange;

import junit.framework.TestCase;

public class TestICSCalendarValidator extends TestCase {
    // Test data constants
    private static final String VALID_TEXT = "Hello World!";
    private static final String TEXT_WITH_NULLS = "Hello\u0000World";
    private static final String TEXT_WITH_INVALID_CHARS = "Hello\u007FWorld";
    private static final String MIXED_TEXT = "Hello\u0000\u0080World\u007F";

    // --- isValidICSContent tests ---

    public void testIsValidICSContent_Null() {
        assertFalse(ICSCalendarValidator.isValidICSContent(null));
    }

    public void testIsValidICSContent_Empty() {
        assertTrue(ICSCalendarValidator.isValidICSContent(""));
    }

    public void testIsValidICSContent_Valid() {
        assertTrue(ICSCalendarValidator.isValidICSContent(VALID_TEXT));
    }

    public void testIsValidICSContent_WithNullBytes() {
        assertFalse(ICSCalendarValidator.isValidICSContent(TEXT_WITH_NULLS));
    }

    public void testIsValidICSContent_WithInvalidChars() {
        assertFalse(ICSCalendarValidator.isValidICSContent(TEXT_WITH_INVALID_CHARS));
    }

    public void testIsValidICSContent_MixedInvalid() {
        assertFalse(ICSCalendarValidator.isValidICSContent(MIXED_TEXT));
    }

    // --- validateWithDetails tests ---

    public void testValidateWithDetails_Null() {
        ICSCalendarValidator.ValidationResult result = ICSCalendarValidator.validateWithDetails(null);
        assertFalse(result.isValid());
        assertEquals("Content is null", result.showReason());
    }

    public void testValidateWithDetails_Valid() {
        ICSCalendarValidator.ValidationResult result = ICSCalendarValidator.validateWithDetails(VALID_TEXT);
        assertTrue(result.isValid());
        assertEquals("", result.showReason());
    }

    public void testValidateWithDetails_NullBytes() {
        ICSCalendarValidator.ValidationResult result = ICSCalendarValidator.validateWithDetails(TEXT_WITH_NULLS);
        assertFalse(result.isValid());
        String reason = result.showReason();
        assertTrue(reason.contains("null byte(s)"));
    }

    public void testValidateWithDetails_InvalidChars() {
        ICSCalendarValidator.ValidationResult result = ICSCalendarValidator.validateWithDetails(TEXT_WITH_INVALID_CHARS);
        assertFalse(result.isValid());
        String reason = result.showReason();
        assertTrue(reason.contains("Invalid character(s)"));
    }

    // --- repairICSContent tests ---

    public void testRepairICSContent_Null() {
        assertNull(ICSCalendarValidator.repairICSContent(null));
    }

    public void testRepairICSContent_Empty() {
        assertEquals("", ICSCalendarValidator.repairICSContent(""));
    }

    public void testRepairICSContent_Unchanged() {
        String original = VALID_TEXT;
        String repaired = ICSCalendarValidator.repairICSContent(original);
        assertEquals(original, repaired);
    }

    public void testRepairICSContent_WithNullBytes() {
        // Invalid chars are dropped, not replaced with space
        String repaired = ICSCalendarValidator.repairICSContent(TEXT_WITH_NULLS);
        assertEquals("HelloWorld", repaired);
    }

    public void testRepairICSContent_WithInvalidChars() {
        // DELETE char (0x7F) is dropped
        String repaired = ICSCalendarValidator.repairICSContent(TEXT_WITH_INVALID_CHARS);
        assertEquals("HelloWorld", repaired);
    }

    public void testRepairICSContent_MultipleInvalid() {
        // All invalid chars (null, C1 control 0x80, DELETE 0x7F) are dropped
        String repaired = ICSCalendarValidator.repairICSContent(MIXED_TEXT);
        assertEquals("HelloWorld", repaired);
    }

    /**
     * Regression test for GitHub issue #533:
     * Null bytes between timezone name and colon produced a spurious space,
     * causing timezone lookup to fail ("W. Europe Standard Time " not found).
     */
    public void testRepairICSContent_Issue533_TimezoneWithTrailingNulls() {
        String input = "TZID:W. Europe Standard Time\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000";
        String repaired = ICSCalendarValidator.repairICSContent(input);
        assertEquals("TZID:W. Europe Standard Time", repaired);
    }

    public void testRepairICSContent_Issue533_PropertyWithNullsBeforeColon() {
        String input = "DTSTART;TZID=W. Europe Standard Time\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000:20210920T143000";
        String repaired = ICSCalendarValidator.repairICSContent(input);
        assertEquals("DTSTART;TZID=W. Europe Standard Time:20210920T143000", repaired);
    }

    public void testRepairICSContent_ControlCharsDropped() {
        // Control chars 0x01-0x08 should be dropped (not TAB/CR/LF)
        String input = "ABC\u0001\u0002\u0003DEF";
        String repaired = ICSCalendarValidator.repairICSContent(input);
        assertEquals("ABCDEF", repaired);
    }

    public void testRepairICSContent_PreservesCRLFandTAB() {
        String input = "LINE1\r\nLINE2\tTABBED";
        String repaired = ICSCalendarValidator.repairICSContent(input);
        assertEquals(input, repaired);
    }

    // --- isValidChar tests ---

    public void testIsValidChar_BasicValid() {
        assertTrue(ICSCalendarValidator.isValidChar('A'));
        assertTrue(ICSCalendarValidator.isValidChar('Z'));
        assertTrue(ICSCalendarValidator.isValidChar('a'));
        assertTrue(ICSCalendarValidator.isValidChar('z'));
        assertTrue(ICSCalendarValidator.isValidChar(' '));
    }

    public void testIsValidChar_BasicInvalid() {
        assertFalse(ICSCalendarValidator.isValidChar('\u0000')); // null byte
        assertFalse(ICSCalendarValidator.isValidChar('\u007F')); // delete char
        assertFalse(ICSCalendarValidator.isValidChar('\u0080')); // C1 control
        assertFalse(ICSCalendarValidator.isValidChar('\u009F')); // C1 control end
    }

    public void testIsValidChar_ControlCharsRejected() {
        // Control chars 0x01-0x08, 0x0B-0x0C, 0x0E-0x1F should be invalid
        assertFalse(ICSCalendarValidator.isValidChar('\u0001'));
        assertFalse(ICSCalendarValidator.isValidChar('\u0008'));
        assertFalse(ICSCalendarValidator.isValidChar('\u000B')); // vertical tab
        assertFalse(ICSCalendarValidator.isValidChar('\u000C')); // form feed
        assertFalse(ICSCalendarValidator.isValidChar('\u000E'));
        assertFalse(ICSCalendarValidator.isValidChar('\u001F'));
    }

    public void testIsValidChar_AllowedWhitespace() {
        assertTrue(ICSCalendarValidator.isValidChar('\t'));  // TAB 0x09
        assertTrue(ICSCalendarValidator.isValidChar('\r'));  // CR 0x0D
        assertTrue(ICSCalendarValidator.isValidChar('\n'));  // LF 0x0A
    }

    public void testIsValidChar_UnicodeAboveC1() {
        assertTrue(ICSCalendarValidator.isValidChar('\u00A0'));  // first valid after C1 range
        assertTrue(ICSCalendarValidator.isValidChar('\u00FF'));
        assertTrue(ICSCalendarValidator.isValidChar('\u4E16'));  // CJK character
    }

    public void testIsValidCRLF() {
        assertTrue(ICSCalendarValidator.isValidChar('\r')); // CR
        assertTrue(ICSCalendarValidator.isValidChar('\n')); // LF
        assertTrue(ICSCalendarValidator.isValidICSContent("BEGIN:VCALENDAR\r\nEND:VCALENDAR"));
        assertTrue(ICSCalendarValidator.validateWithDetails("BEGIN:VCALENDAR\r\nEND:VCALENDAR").isValid());
    }

    /**
     * Verify isValidChar and VALID_CHARS_PATTERN agree on all chars in the BMP.
     * This prevents future inconsistencies between the two validation paths.
     */
    public void testIsValidChar_ConsistentWithPattern() {
        for (int i = 0; i < 0x200; i++) {
            char c = (char) i;
            String s = String.valueOf(c);
            boolean patternSays = ICSCalendarValidator.isValidICSContent(s);
            boolean methodSays = ICSCalendarValidator.isValidChar(c);
            assertEquals("Mismatch at U+" + String.format("%04X", i), patternSays, methodSays);
        }
    }
}
