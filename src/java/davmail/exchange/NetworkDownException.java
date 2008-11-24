package davmail.exchange;

import java.io.IOException;

/**
 * Custom exception to mark network down case.
 */
public class NetworkDownException extends IOException {
    public NetworkDownException(String message) {
        super(message);
    }
}
