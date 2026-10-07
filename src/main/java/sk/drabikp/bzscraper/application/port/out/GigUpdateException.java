package sk.drabikp.bzscraper.application.port.out;

/** Thrown when editing a published gig on a platform fails. */
public class GigUpdateException extends Exception {

    public GigUpdateException(String message) {
        super(message);
    }

    public GigUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
