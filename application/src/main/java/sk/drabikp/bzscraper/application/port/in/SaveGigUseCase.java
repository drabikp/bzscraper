package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Gig;

/**
 * Adds a new gig to the catalog (the source of truth). A gig never overwrites another: when the
 * catalog already has a gig with its identity (same day and venue), {@link GigIdentityTakenException}
 * — edit that one instead.
 */
public interface SaveGigUseCase {

    void add(Gig gig);
}
