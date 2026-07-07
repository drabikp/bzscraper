package sk.drabikp.bzscraper.application.port.out;

/**
 * Thrown when a Bandsintown portal CSV upload cannot be completed — login/2FA
 * failure, an unexpected auth step-up (email verification / "unusual login"),
 * a rejected import, or a browser/timeout error. The message is surfaced to the
 * user; the gigs in the failed batch are NOT marked uploaded, so they retry.
 */
public class BitUploadException extends Exception {

    public BitUploadException(String message) {
        super(message);
    }

    public BitUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
