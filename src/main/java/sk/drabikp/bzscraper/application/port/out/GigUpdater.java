package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;

/**
 * Overwrites a previously published gig's details on one platform, in place, addressed
 * by the platform's external id (which stays the same). One implementation per platform
 * that supports editing; the resync use case collects them via an injected {@code List}.
 */
public interface GigUpdater {

    Platform platform();

    void update(String externalRef, Gig gig) throws GigUpdateException;

    /** Whether the platform takes changes to events that are already over; if not, they are never tried. */
    boolean updatesPastEvents();
}
