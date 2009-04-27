package davmail.exchange;

import davmail.exception.DavMailException;

/**
 * Custom exception to mark network down case.
 */
public class NetworkDownException extends DavMailException {
    public NetworkDownException(String key) {
        super(key);
    }
}
