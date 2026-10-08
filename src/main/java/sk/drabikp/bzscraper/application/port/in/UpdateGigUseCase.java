package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.QueueResult;

/**
 * Replaces an existing catalog gig with an edited version. If the edit changes the
 * gig's identity (its date or venue), the old entry is removed so exactly one row
 * represents the gig. Every platform the gig is on gets an update queued in the same
 * transaction; the platforms follow when the sync worker gets to it.
 */
public interface UpdateGigUseCase {

    QueueResult update(GigId originalId, Gig updated);
}
