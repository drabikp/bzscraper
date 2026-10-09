package sk.drabikp.bzscraper.gig.application;

import java.util.Map;

/**
 * A rule of the catalog or the sync said no, and nothing was changed; the message is for the
 * user ("The gig was changed meanwhile — …"). Anything else that goes wrong is a fault, not
 * something to show raw. {@link #code()} and {@link #args()} let a page say it in the user's
 * language; the message is the English text and the fallback.
 */
public class UserFacingException extends RuntimeException {

    private final String code;
    private final Map<String, String> args;

    public UserFacingException(String message) {
        this(null, Map.of(), message, null);
    }

    public UserFacingException(String message, Throwable cause) {
        this(null, Map.of(), message, cause);
    }

    public UserFacingException(String code, Map<String, String> args, String message) {
        this(code, args, message, null);
    }

    public UserFacingException(String code, Map<String, String> args, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.args = Map.copyOf(args);
    }

    /** What kind of refusal it is (e.g. {@code gigBusy}); null when only the message says it. */
    public String code() {
        return code;
    }

    /** The values the message is built from, by name. */
    public Map<String, String> args() {
        return args;
    }
}
