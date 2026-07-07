package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.util.Collection;

/**
 * Outbound port for the local "already-uploaded" set that makes publishing
 * idempotent without knowing platform-assigned IDs. A gig whose {@link GigId} is
 * in the set for a platform is not re-published. Entries are added only after the
 * platform confirms acceptance, so a failed upload retries next run.
 */
public interface UploadedGigStore {

    boolean isUploaded(Platform platform, GigId key);

    void markUploaded(Platform platform, Collection<GigId> keys);
}
