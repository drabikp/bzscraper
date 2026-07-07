package sk.drabikp.bzscraper.application.port.out;

/**
 * Thrown when creating a single gig on the Bandzone band admin fails — login
 * failure, a wizard step not reachable, city autocomplete unresolved, or a
 * validation/timeout error. Per-gig: one failure does not abort the rest of the
 * batch, and the failed gig is NOT marked uploaded, so it retries.
 */
public class BandzoneUploadException extends Exception {

    public BandzoneUploadException(String message) {
        super(message);
    }

    public BandzoneUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
