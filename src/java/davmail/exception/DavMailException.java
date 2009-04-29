package davmail.exception;

import davmail.BundleMessage;

import java.io.IOException;
import java.util.Locale;

/**
 * I18 IOException subclass.
 */
public class DavMailException extends IOException {
    private final BundleMessage message;

    public DavMailException(String key, Object... arguments) {
         this.message = new BundleMessage(key, arguments);
    }

    @Override
    public String getMessage() {
        return message.format();
    }

    public String getMessage(Locale locale) {
        return message.format(locale);
    }

    public String getLogMessage() {
        return message.formatLog();
    }

    public BundleMessage getBundleMessage() {
        return message;
    }
}
