package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.util.Optional;

/**
 * Local record of what has been published to each platform. Keeps idempotency (a
 * gig already published is not re-published) and, when the platform gives one back,
 * the platform's external id ({@code externalRef}) so the gig can later be updated,
 * cancelled or deleted on that platform. Keyed by {@link Platform} + {@link GigId}.
 */
public interface PublishedGigStore {

    boolean isPublished(Platform platform, GigId gigId);

    /** The platform's id for a published gig, if the platform returned one. */
    Optional<String> externalRef(Platform platform, GigId gigId);

    /** Records a successful publish; {@code externalRef} may be null if unknown. */
    void record(Platform platform, GigId gigId, String externalRef);

    /** Forgets a gig on a platform (after it has been removed there). */
    void remove(Platform platform, GigId gigId);
}
