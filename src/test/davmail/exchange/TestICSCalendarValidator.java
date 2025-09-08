// auto generated testcases based on code
// class under test is davmail/src/java/davmail/exchange/ICSCalendarValidator.java

package davmail.exchange;

import junit.framework.TestCase;

public class TestICSCalendarValidator extends TestCase {
    // Test data constants
    private static final String VALID_TEXT = "Hello World!";
    private static final String TEXT_WITH_NULLS = "Hello\u0000World";
    private static final String TEXT_WITH_INVALID_CHARS = "Hello\u007FWorld";
    private static final String MIXED_TEXT = "Hello\u0000\u0080World\u007F";

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
        String original = TEXT_WITH_NULLS;
        String repaired = ICSCalendarValidator.repairICSContent(original);
        assertEquals("Hello World", repaired);
    }

    public void testRepairICSContent_WithInvalidChars() {
        String original = TEXT_WITH_INVALID_CHARS;
        String repaired = ICSCalendarValidator.repairICSContent(original);
        assertEquals("Hello World", repaired);
    }

    public void testRepairICSContent_MultipleInvalid() {
        String original = MIXED_TEXT;
        String repaired = ICSCalendarValidator.repairICSContent(original);
        assertEquals("Hello World", repaired);
    }

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
        assertFalse(ICSCalendarValidator.isValidChar('\u0080')); // invalid Unicode
    }

    public void testIsValidCRLF() {
        assertTrue(ICSCalendarValidator.isValidChar('\r')); // CR
        assertTrue(ICSCalendarValidator.isValidChar('\n')); // LF
        assertTrue(ICSCalendarValidator.isValidICSContent("BEGIN:VCALENDAR\r\nEND:VCALENDAR"));
        assertTrue(ICSCalendarValidator.validateWithDetails("BEGIN:VCALENDAR\r\nEND:VCALENDAR").isValid());
    }
}
