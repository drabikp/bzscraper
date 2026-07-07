package sk.drabikp.bzscraper.application.port.out;

/** Thrown when cancelling or deleting a gig on a platform fails. */
public class GigWithdrawalException extends Exception {

    public GigWithdrawalException(String message) {
        super(message);
    }

    public GigWithdrawalException(String message, Throwable cause) {
        super(message, cause);
    }
}
