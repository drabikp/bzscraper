package sk.drabikp.bzscraper.application.port.out;

/**
 * Thrown when creating a single gig on the Bandzone band admin fails — login
 * failure, a wizard step not reachable, city autocomplete unresolved, or a
 * validation/timeout error. Per-gig: one failure does not abort the rest of the
 * batch, and the failed gig is NOT marked uploaded, so it retries.
 */
public class BandzoneUploadException extends Exception {

    private final boolean permanent;

    public BandzoneUploadException(String message) {
        this(message, null, false);
    }

    public BandzoneUploadException(String message, Throwable cause) {
        this(message, cause, false);
    }

    /** @param permanent trying again won't help: the platform refused the data, or the platform is switched off */
    public BandzoneUploadException(String message, Throwable cause, boolean permanent) {
        super(message, cause);
        this.permanent = permanent;
    }

    /** Trying again won't help: the platform refused the data, or the platform is switched off. */
    public boolean permanent() {
        return permanent;
    }
}
