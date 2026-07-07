package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Publishes gigs to one or more platforms. Idempotent per platform (gigs already
 * uploaded are skipped). Returns one {@link PublishResult} per (platform, gig),
 * so the caller can render outcomes across every target from a single flat list.
 */
public interface PublishGigsUseCase {

    List<PublishResult> publish(Set<Platform> targets, Collection<Gig> gigs);

    /** Convenience for a single platform. */
    default List<PublishResult> publish(Platform target, Collection<Gig> gigs) {
        return publish(Set.of(target), gigs);
    }
}
