package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.PlatformResult;

import java.util.List;

/**
 * Pushes catalog changes of a gig to every platform it was published to. Platforms
 * where the gig was never published are skipped; each method returns one result per
 * platform actually contacted.
 */
public interface ResyncGigUseCase {

    /**
     * Overwrites the platform copies with the gig's current details. {@code previousId}
     * is the identity the gig was published under — it differs from {@code current.id()}
     * when the edit changed the gig's date or venue.
     */
    List<PlatformResult> pushEdit(GigId previousId, Gig current);

    /**
     * Makes the platform copies of a gig that was cancelled there active again.
     * {@code gig} is the reactivated (not cancelled) catalog gig.
     */
    List<PlatformResult> reactivate(Gig gig);
}
