package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.ReconciliationResult;

import java.util.Collection;
import java.util.Set;

/**
 * Imports gigs from a platform and reconciles them against the local catalog, then
 * applies the gigs the user chose (adds/updates). The catalog stays the source of
 * truth; import never deletes.
 */
public interface ImportGigsUseCase {

    Set<Platform> importablePlatforms();

    ReconciliationResult reconcile(Platform platform);

    void apply(Collection<Gig> gigs);
}
