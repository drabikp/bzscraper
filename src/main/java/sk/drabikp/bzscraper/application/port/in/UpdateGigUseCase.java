package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;

/**
 * Replaces an existing catalog gig with an edited version. If the edit changes the
 * gig's identity (its date or venue), the old entry is removed so exactly one row
 * represents the gig.
 */
public interface UpdateGigUseCase {

    void update(GigId originalId, Gig updated);
}
