package sk.drabikp.bzscraper.application.port.out;

/**
 * Thrown when a Bandsintown artist-portal operation cannot be completed — login or
 * authenticator-code failure, an unexpected auth step-up, a rejected upload, or a
 * browser/timeout error. The message is surfaced to the user.
 */
public class BitUploadException extends Exception {

    public BitUploadException(String message) {
        super(message);
    }

    public BitUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
