package sk.drabikp.bzscraper.application.port.in;

/**
 * A rule of the catalog or the sync said no, and nothing was changed; the message is for the
 * user as it is ("The gig was changed meanwhile — …"). Anything else that goes wrong is a fault,
 * not something to show raw.
 */
public class UserFacingException extends RuntimeException {

    public UserFacingException(String message) {
        super(message);
    }

    public UserFacingException(String message, Throwable cause) {
        super(message, cause);
    }
}
