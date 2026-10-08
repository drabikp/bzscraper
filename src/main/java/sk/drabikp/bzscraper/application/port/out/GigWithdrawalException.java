package sk.drabikp.bzscraper.application.port.out;

/** Thrown when cancelling or deleting a gig on a platform fails. */
public class GigWithdrawalException extends Exception {

    private final boolean permanent;

    public GigWithdrawalException(String message) {
        this(message, null, false);
    }

    public GigWithdrawalException(String message, Throwable cause) {
        this(message, cause, false);
    }

    /** @param permanent trying again won't help: the platform refused the data, or the platform is switched off */
    public GigWithdrawalException(String message, Throwable cause, boolean permanent) {
        super(message, cause);
        this.permanent = permanent;
    }

    /** Trying again won't help: the platform refused the data, or the platform is switched off. */
    public boolean permanent() {
        return permanent;
    }
}
