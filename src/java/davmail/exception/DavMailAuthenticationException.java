package davmail.exception;

import davmail.exception.DavMailException;

/**
 * I18 AuthenticationException subclass.
 */
public class DavMailAuthenticationException extends DavMailException {
    public DavMailAuthenticationException(String key) {
        super(key);
    }
}
