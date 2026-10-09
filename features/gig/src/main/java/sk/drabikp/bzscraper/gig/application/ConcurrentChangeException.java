package sk.drabikp.bzscraper.gig.application;

import java.util.Map;

/**
 * The data was changed by someone else since it was read — another window, an import, the
 * calendar — so the change was NOT made (optimistic locking: nothing is locked while the user
 * edits; a stale edit is refused when it is saved). Reload and do it again.
 */
public class ConcurrentChangeException extends UserFacingException {

    /** The gig is gone. */
    public static final String DELETED = "deletedMeanwhile";
    /** The gig is not as the user saw it. */
    public static final String CHANGED = "changedMeanwhile";

    public ConcurrentChangeException(String code, String message) {
        super(code, Map.of(), message);
    }

    public ConcurrentChangeException(String code, String message, Throwable cause) {
        super(code, Map.of(), message, cause);
    }
}
