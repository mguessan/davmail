package davmail.exchange;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;

/**
 * Validator for iCalendar data according to RFC 5545 specifications.
 * This implementation provides comprehensive validation and repair capabilities for iCalendar content,
 * specifically focusing on character validation rather than XML structure.
 *
 * This helpful tool addresses synchronization issues between different calendar clients
 * (OWA, Outlook, and Thunderbird via DavMail) where calendar entries containing invalid
 * characters are handled differently across platforms. These problematic entries originate
 * from the MS Exchange Server, where they were either stored with invalid characters or
 * became corrupted during storage. While OWA and Outlook silently hide such entries without
 * displaying them to users, Thunderbird logs XML parse errors in its error console. The
 * validator provides detailed validation information about invalid characters and offers
 * repair functionality to automatically remove problematic characters while preserving
 * valid content.
 *
 * The implementation was developed to address a specific issue where calendar entries
 * containing invalid string content are hidden in OWA and Outlook, making them inaccessible
 * for manual deletion or repair. Since these entries are not visible in OWA and Outlook,
 * users cannot remove or fix them before synchronization to Thunderbird, where they cause
 * XML parsing errors. The solution provides a way to detect and repair these problematic
 * entries, which could otherwise be handled by DavMail during the synchronization process.
 * The issue is documented in Bugzilla at https://bugzilla.mozilla.org/show_bug.cgi?id=1941840.
 *
 * @author ifrh (<a href="https://github.com/ifrh">GitHub</a>)
 * @author ifrh (<a href="https://sourceforge.net/u/ifrh/profile/">SourceForge</a>)
 * @since 2025-02-05 // yyyy-mm-dd
 */

public class ICSCalendarValidator {
    protected static final Logger LOGGER = Logger.getLogger(ICSCalendarValidator.class);
    // Optimized pattern for validation
    private static final Pattern VALID_CHARS_PATTERN =
            Pattern.compile("^[\\x20-\\x7E\u0080-\uFFFF]*$");
    // Constants for better readability
    private static final char NULL_BYTE = '\u0000';
    private static final char SPACE = ' ';
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
            } else if ((c < 32 || c == DELETE || (c >= 128 && c <= 159))) {
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
                    invalidChars.substring(0, invalidChars.length() - 1));
        }

        return new ValidationResult(issues.length() == 0, issues.toString());
    }

    /**
     * Repairs an iCalendar string by removing invalid characters.
     * Replaces multiple consecutive invalid characters with a single space.
     * @param content The string to repair
     * @return The repaired string
     */
    public static String repairICSContent(String content) {
        if (content == null) return null;
        String message ="ICSCalendarValidator repair characters in ICS content:";

        StringBuilder repaired = new StringBuilder();
        boolean lastWasInvalid = false;

        for (char c : content.toCharArray()) {
            if (isValidChar(c)) {
                repaired.append(c);
                lastWasInvalid = false;
            } else if (!lastWasInvalid) {
                repaired.append(SPACE);
                lastWasInvalid = true;
            }
        }
        String fixed = repaired.toString().trim();
        // just put output to debug logger, only if some invalid characters has been changed.
        if (!content.equals(fixed)){
            LOGGER.debug ( message + "\n[" + content + "]\n => [" + fixed + "]\n fix complete.");
        }
        return fixed ;
    }

    /**
     * Checks if a single character is valid.
     * A character is valid if it is:
     * - Not a control character (ASCII 0-31)
     * - Not a delete character (ASCII 127)
     * - Not an invalid Unicode character (128-159)
     * @param c The character to check
     * @return true if the character is valid
     */
    static boolean isValidChar(char c) {
        return c > 0 && !(c == DELETE || (c >= 128 && c <= 159));
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
