package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.QueueResult;

import java.util.Collection;
import java.util.Set;

/**
 * Queues gigs for publishing on one or more platforms. Gigs already published (or
 * already queued) there, and cancelled gigs, are left out with the reason.
 */
public interface PublishGigsUseCase {

    QueueResult publish(Set<Platform> targets, Collection<Gig> gigs);
}
