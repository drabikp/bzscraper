package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.application.port.in.UserFacingException;

/**
 * The data was changed by someone else since it was read — another window, an import, the
 * calendar — so the change was NOT made (optimistic locking: nothing is locked while the user
 * edits; a stale edit is refused when it is saved). Reload and do it again.
 */
public class ConcurrentChangeException extends UserFacingException {

    public ConcurrentChangeException(String message) {
        super(message);
    }

    public ConcurrentChangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
