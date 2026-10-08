package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.QueueResult;

/**
 * Marks a catalog gig cancelled, or reactivates a cancelled one, and queues the same
 * change for every platform the gig is on (in the same transaction).
 */
public interface CancelGigUseCase {

    QueueResult cancel(GigId id);

    QueueResult reactivate(GigId id);
}
