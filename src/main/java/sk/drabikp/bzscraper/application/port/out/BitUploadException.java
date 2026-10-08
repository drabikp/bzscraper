package sk.drabikp.bzscraper.application.port.out;

/**
 * Thrown when a Bandsintown artist-portal operation cannot be completed — login or
 * authenticator-code failure, an unexpected auth step-up, a rejected upload, or a
 * browser/timeout error. The message is surfaced to the user.
 */
public class BitUploadException extends Exception {

    private final boolean permanent;

    public BitUploadException(String message) {
        this(message, null, false);
    }

    public BitUploadException(String message, Throwable cause) {
        this(message, cause, false);
    }

    /** @param permanent trying again won't help: the platform refused the data, or the platform is switched off */
    public BitUploadException(String message, Throwable cause, boolean permanent) {
        super(message, cause);
        this.permanent = permanent;
    }

    /** Trying again won't help: the platform refused the data, or the platform is switched off. */
    public boolean permanent() {
        return permanent;
    }
}
