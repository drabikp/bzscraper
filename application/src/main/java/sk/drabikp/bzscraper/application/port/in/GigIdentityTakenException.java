package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Gig;

/**
 * Another catalog gig already has this identity (the same day and venue): saving would
 * overwrite it and its platform links, so nothing was changed.
 */
public class GigIdentityTakenException extends UserFacingException {

    public GigIdentityTakenException(Gig gig) {
        super("The catalog already has a gig on " + gig.schedule().startDate() + " at "
                + gig.location().displayVenue() + " — edit that one instead.");
    }
}
