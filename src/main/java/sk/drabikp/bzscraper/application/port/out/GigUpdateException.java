package sk.drabikp.bzscraper.application.port.out;

/** Thrown when editing a published gig on a platform fails. */
public class GigUpdateException extends Exception {

    private final boolean permanent;

    public GigUpdateException(String message) {
        this(message, null, false);
    }

    public GigUpdateException(String message, Throwable cause) {
        this(message, cause, false);
    }

    /** @param permanent trying again won't help: the platform refused the data, or the platform is switched off */
    public GigUpdateException(String message, Throwable cause, boolean permanent) {
        super(message, cause);
        this.permanent = permanent;
    }

    /** Trying again won't help: the platform refused the data, or the platform is switched off. */
    public boolean permanent() {
        return permanent;
    }
}
