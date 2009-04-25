package davmail;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Internationalization message.
 */
public class BundleMessage {
    protected static final String MESSAGE_BUNDLE_NAME = "davmailmessages";
    protected final String key;
    private final Object[] arguments;

    public BundleMessage(String key, Object... arguments) {
        this.key = key;
        this.arguments = arguments;
    }

    protected ResourceBundle getBundle(Locale locale) {
        if (locale == null) {
            return ResourceBundle.getBundle(MESSAGE_BUNDLE_NAME);
        } else {
            return ResourceBundle.getBundle(MESSAGE_BUNDLE_NAME, locale);
        }
    }

    public String format() {
        return format(null);
    }

    public String format(Locale locale) {
        Object[] formattedArguments = null;
        if (arguments != null) {
            formattedArguments = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i] instanceof BundleMessage) {
                    formattedArguments[i] = ((BundleMessage) arguments[i]).format(locale);
                } else if (arguments[i] instanceof BundleMessageList) {
                    StringBuilder buffer = new StringBuilder();
                    for (BundleMessage bundleMessage:(BundleMessageList)arguments[i]) {
                        buffer.append(bundleMessage.format(locale));
                    }
                    formattedArguments[i] = buffer.toString();
                } else {
                    formattedArguments[i] = arguments[i];
                }
            }
        }
        return MessageFormat.format(getBundle(locale).getString(key), formattedArguments);
    }

    public static String format(String key, Object... arguments) {
        return MessageFormat.format(ResourceBundle.getBundle(MESSAGE_BUNDLE_NAME).getString(key), arguments);
    }

    public static class BundleMessageList extends ArrayList<BundleMessage>{}
}
