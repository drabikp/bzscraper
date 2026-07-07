package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.ImportGigsUseCase;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.ReconciliationResult;
import sk.drabikp.bzscraper.domain.service.GigReconciliation;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Import orchestrator: pulls gigs from a platform importer, reconciles them against
 * the catalog via {@link GigReconciliation}, and applies the user's chosen gigs.
 * A new importable platform is one more {@link GigImporter} bean — this class does
 * not change.
 */
public class GigImportService implements ImportGigsUseCase {

    private final Map<Platform, GigImporter> importers;
    private final GigRepository gigRepository;

    public GigImportService(List<GigImporter> importers, GigRepository gigRepository) {
        this.importers = new EnumMap<>(Platform.class);
        for (GigImporter importer : importers) {
            GigImporter existing = this.importers.put(importer.platform(), importer);
            if (existing != null) {
                throw new IllegalStateException("Two GigImporters registered for platform " + importer.platform());
            }
        }
        this.gigRepository = gigRepository;
    }

    @Override
    public Set<Platform> importablePlatforms() {
        return Set.copyOf(importers.keySet());
    }

    @Override
    public ReconciliationResult reconcile(Platform platform) {
        GigImporter importer = importers.get(platform);
        if (importer == null) {
            throw new IllegalArgumentException("No GigImporter registered for platform " + platform);
        }
        List<Gig> imported = importer.importGigs();
        List<Gig> local = gigRepository.findAll();
        return GigReconciliation.reconcile(local, imported);
    }

    @Override
    public void apply(Collection<Gig> gigs) {
        gigs.forEach(gigRepository::save);
    }
}
