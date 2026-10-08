package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.QueueResult;

import java.util.Collection;

/**
 * Queues an update of the gigs' platform copies to their current catalog details — for
 * when a platform copy was changed by hand or an earlier update was given up on.
 */
public interface ResyncGigUseCase {

    QueueResult resync(Collection<GigId> gigs);
}
