package davmail;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Internationalization message.
 */
public class BundleMessage {
    protected static final String MESSAGE_BUNDLE_NAME = "davmailmessages"; 
    protected static final ResourceBundle MESSAGE_BUNDLE = ResourceBundle.getBundle(MESSAGE_BUNDLE_NAME);
    protected final String key;
    private final Object[] arguments;

    public BundleMessage(String key, Object ... arguments) {
        this.key = key;
        this.arguments = arguments;
    }

    public String format() {
       return MessageFormat.format(MESSAGE_BUNDLE.getString(key), arguments);
    }

    public static String format(String key, Object ... arguments) {
        return MessageFormat.format(MESSAGE_BUNDLE.getString(key), arguments);
    }

}
