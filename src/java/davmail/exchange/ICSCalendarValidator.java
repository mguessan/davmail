package davmail.exchange;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;

/**
 * Validator for iCalendar data according to RFC 5545 specifications.
 * This implementation provides comprehensive validation and repair capabilities for iCalendar content,
 * specifically focusing on character validation rather than XML structure.
 * <p>
 * This helpful tool addresses synchronization issues between different calendar clients
 * (OWA, Outlook, and Thunderbird via DavMail) where calendar entries containing invalid
 * characters are handled differently across platforms. These problematic entries originate
 * from the MS Exchange Server, where they were either stored with invalid characters or
 * became corrupted during storage. While OWA and Outlook silently hide such entries without
 * displaying them to users, Thunderbird logs XML parse errors in its error console. The
 * validator provides detailed validation information about invalid characters and offers
 * repair functionality to automatically remove problematic characters while preserving
 * valid content.
 * <p>
 * The implementation was developed to address a specific issue where calendar entries
 * containing invalid string content are hidden in OWA and Outlook, making them inaccessible
 * for manual deletion or repair. Since these entries are not visible in OWA and Outlook,
 * users cannot remove or fix them before synchronization to Thunderbird, where they cause
 * XML parsing errors. The solution provides a way to detect and repair these problematic
 * entries, which could otherwise be handled by DavMail during the synchronization process.
 * The issue is documented in Bugzilla at <a href="https://bugzilla.mozilla.org/show_bug.cgi?id=1941840">...</a>.
 *
 * @author ifrh (<a href="https://github.com/ifrh">GitHub</a>)
 * @author ifrh (<a href="https://sourceforge.net/u/ifrh/profile/">SourceForge</a>)
 * @since 2025-02-05 // yyyy-mm-dd
 */

public class ICSCalendarValidator {
    protected static final Logger LOGGER = Logger.getLogger(ICSCalendarValidator.class);
    // Optimized pattern for validation, tab, CR and LF are allowed in icalendar content
    // Excludes C1 control characters (0x80-0x9F), consistent with isValidChar() and validateWithDetails()
    private static final Pattern VALID_CHARS_PATTERN =
            Pattern.compile("^[\r\n\t\\x20-\\x7E\u00A0-\uFFFF]*$");

    // Constants for better readability
    private static final char NULL_BYTE = '\u0000';
    private static final char DELETE = '\u007F';

    /**
     * Validates whether a string contains only valid characters for iCalendar content.
     * Ensures the string contains no null bytes and only printable ASCII characters,
     * while allowing properly encoded Unicode characters.
     * @param content The string to validate
     * @return true if all characters are valid
     */
    public static boolean isValidICSContent(String content) {
        return content != null && VALID_CHARS_PATTERN.matcher(content).matches();
    }

    /**
     * Returns detailed validation information about the content.
     * @param content The string to validate
     * @return ValidationResult object containing details
     */
    public static ValidationResult validateWithDetails(String content) {
        if (content == null) {
            return new ValidationResult(false, "Content is null");
        }

        // Efficient validation checking all conditions in one pass
        StringBuilder issues = new StringBuilder();
        int nullByteCount = 0;
        StringBuilder invalidChars = new StringBuilder();

        for (char c : content.toCharArray()) {
            if (c == NULL_BYTE) {
                nullByteCount++;
            } else if (((c < 32 && c!='\r' && c!='\n' && c!='\t') || c == DELETE || (c >= 128 && c <= 159))) {
                invalidChars.append(String.format("\\u%04x,", (int)c));
            }
        }

        // Collect all found problems
        if (nullByteCount > 0) {
            issues.append(nullByteCount).append(" null byte(s) found");
        }
        if (invalidChars.length() > 0) {
            if (issues.length() > 0) issues.append(", ");
            issues.append("Invalid character(s): ").append(
                    invalidChars, 0, invalidChars.length() - 1);
        }

        return new ValidationResult(issues.length() == 0, issues.toString());
    }

    /**
     * Repairs an iCalendar string by removing invalid characters.
     * Invalid characters are dropped rather than replaced to avoid
     * introducing unexpected spaces in ICS property values.
     * @param content The string to repair
     * @return The repaired string with invalid characters removed
     */
    public static String repairICSContent(String content) {
        if (content == null) return null;
        String message = "ICSCalendarValidator repair characters in ICS content:";

        StringBuilder repaired = new StringBuilder();

        for (char c : content.toCharArray()) {
            if (isValidChar(c)) {
                repaired.append(c);
            }
        }
        String fixed = repaired.toString();
        // just put output to debug logger, only if some invalid characters have been removed.
        if (!content.equals(fixed)) {
           // For logging, invalid characters (e.g. null bytes) are represented as Unicode hex escapes (\uXXXX)
           // some editors cannot handle null bytes in text files, see comment https://github.com/mguessan/davmail/issues/533#issue-5601924334
            StringBuilder escapedContent = new StringBuilder(content.length() + (content.length()-fixed.length())*6);
            for (char c : content.toCharArray()) {
                if (!isValidChar(c)) {
                    escapedContent.append(String.format("\\u%04x", (int) c));
                } else {
                    escapedContent.append(c);
                }
            }
            
            LOGGER.debug(message + "\n[" + escapedContent.toString() + "]\n => [" + fixed + "]\n fix complete.");
        }
        return fixed;
    }

    /**
     * Checks if a single character is valid for iCalendar content.
     * A character is valid if it is:
     * - A tab (0x09), carriage return (0x0D), or line feed (0x0A)
     * - A printable ASCII character (0x20-0x7E)
     * - A Unicode character above U+007F (0x80-0xFFFF), excluding C1 control range (0x80-0x9F)
     * This is consistent with VALID_CHARS_PATTERN.
     * @param c The character to check
     * @return true if the character is valid
     */
    static boolean isValidChar(char c) {
        // Allow TAB, CR, LF
        if (c == '\t' || c == '\r' || c == '\n') {
            return true;
        }
        // Printable ASCII range (space through tilde)
        if (c >= 0x20 && c <= 0x7E) {
            return true;
        }
        // Valid Unicode above ASCII, excluding C1 control characters (0x80-0x9F)
        return c >= 0xA0;
    }

    /**
     * Result structure for validation results.
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final String reason;

        public ValidationResult(boolean isValid, String reason) {
            this.isValid = isValid;
            this.reason = reason;
        }

        public boolean isValid() { return isValid; }
        public String showReason() { return reason; }
    }
}
