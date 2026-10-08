package sk.drabikp.bzscraper.application.port.in;

/** The gig's platform work is running right now; changing the gig must wait until it is done. */
public class GigBusyException extends UserFacingException {

    public GigBusyException(String gigLabel) {
        super(gigLabel + ": its platform work is running right now — try again when it is done.");
    }
}
