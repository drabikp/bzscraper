package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.SyncAction;

import java.util.Optional;

/** Whether some step can do the work, asked when it is queued: why not, or empty when it can. */
public interface SyncAdmission {

    /** Everything is admitted (tests that don't care about workflows). */
    SyncAdmission ALL = (platform, action, gig) -> Optional.empty();

    Optional<String> leftOut(Platform platform, SyncAction action, Gig gig);
}
