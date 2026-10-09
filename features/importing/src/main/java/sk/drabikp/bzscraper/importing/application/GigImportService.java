package sk.drabikp.bzscraper.importing.application;

import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.application.port.out.Transactions;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.platform.Platform;
import sk.drabikp.bzscraper.gig.domain.platform.Platforms;
import sk.drabikp.bzscraper.importing.application.port.in.ImportGigsUseCase;
import sk.drabikp.bzscraper.importing.application.port.out.GigImporter;
import sk.drabikp.bzscraper.importing.application.port.out.PlatformReads;
import sk.drabikp.bzscraper.importing.domain.GigMerge;
import sk.drabikp.bzscraper.importing.domain.ImportDecision;
import sk.drabikp.bzscraper.importing.domain.ImportPlan;
import sk.drabikp.bzscraper.importing.domain.ImportPlanner;
import sk.drabikp.bzscraper.importing.domain.ImportProposal;
import sk.drabikp.bzscraper.importing.domain.ImportResult;
import sk.drabikp.bzscraper.importing.domain.ImportedGig;
import sk.drabikp.bzscraper.sync.application.port.out.PlatformException;
import sk.drabikp.bzscraper.sync.domain.QueueResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Import orchestrator: reads the chosen platforms through their {@link GigImporter}s,
 * plans with {@link ImportPlanner}, and applies the user's decisions — saving the merged
 * gig ({@link GigMerge}) and recording each platform copy's id against it — in one
 * transaction. A new importable platform is one more {@code GigImporter} bean.
 */
public class GigImportService implements ImportGigsUseCase {

    private final Map<Platform, GigImporter> importers = new TreeMap<>();
    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final Transactions transactions;
    private final Platforms platforms;
    private final GigWrites writes;

    public GigImportService(List<GigImporter> importers, GigRepository gigRepository,
                            PublishedGigStore publishedGigStore, Transactions transactions, Platforms platforms,
                            GigWrites writes) {
        for (GigImporter importer : importers) {
            if (this.importers.put(importer.platform(), importer) != null) {
                throw new IllegalStateException("Two GigImporters registered for platform " + importer.platform());
            }
        }
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.transactions = transactions;
        this.platforms = platforms;
        this.writes = writes;
    }

    @Override
    public Set<Platform> importablePlatforms() {
        return new LinkedHashSet<>(platforms.ordered(importers.keySet()));
    }

    @Override
    public ImportPlan plan(Set<Platform> from) {
        List<ImportedGig> imported = new ArrayList<>();
        Map<Platform, String> failures = new TreeMap<>();
        for (Platform platform : from) {
            GigImporter importer = importers.get(platform);
            if (importer == null) {
                failures.put(platform, "Importing from this platform is not supported.");
                continue;
            }
            try {
                imported.addAll(importer.importGigs());
            } catch (PlatformException e) {
                failures.put(platform, e.getMessage());
            } catch (RuntimeException e) {
                failures.put(platform, PlatformReads.unexpected(platform, e));
            }
        }
        return ImportPlanner.plan(gigRepository.findAll(), publishedGigStore.all(), imported, failures, platforms);
    }

    @Override
    public ImportResult apply(List<ImportDecision> decisions) {
        int[] counts = new int[4];             // added, updated, linked, skipped
        List<QueueResult> queued = new ArrayList<>();
        transactions.inTransaction(() -> {
            for (ImportDecision decision : decisions) {
                if (!decision.include()) {
                    continue;
                }
                ImportProposal proposal = decision.proposal();
                if (proposal.suggested() && !decision.sameGig()) {
                    proposal.copies().forEach(copy -> importOnItsOwn(copy, counts));
                } else {
                    queued.add(importTogether(proposal, decision.chosen(), counts));
                }
            }
        });
        queued.forEach(writes::signal);
        return new ImportResult(counts[0], counts[1], counts[2], counts[3]);
    }

    /**
     * The chosen version, filled in from the others, becomes the catalog gig — through the
     * catalog's own write path: a new gig must not overwrite another; an existing one is
     * replaced only as it was when the plan was read, and its platforms (those it was on
     * before this import) get the update. Then the imported copies are linked to it.
     */
    private QueueResult importTogether(ImportProposal proposal, ImportProposal.Version chosen, int[] counts) {
        Gig merged = GigMerge.merge(chosen, proposal.versions(), platforms);
        Gig local = proposal.local();
        QueueResult queued = QueueResult.NOTHING;
        if (local == null) {
            writes.add(merged);
            counts[0]++;
        } else if (!merged.equals(local)) {
            queued = writes.replace(local, merged);
            counts[1]++;
        }
        for (ImportedGig copy : proposal.copies()) {
            publishedGigStore.record(copy.platform(), merged.id(), copy.externalRef());
            counts[2]++;
        }
        return queued;
    }

    /** A suggested match the user rejected: the copy becomes its own catalog gig. */
    private void importOnItsOwn(ImportedGig copy, int[] counts) {
        if (gigRepository.findById(copy.gig().id()).isPresent()) {
            counts[3]++;                         // would overwrite another catalog gig
            return;
        }
        writes.add(copy.gig());
        publishedGigStore.record(copy.platform(), copy.gig().id(), copy.externalRef());
        counts[0]++;
        counts[2]++;
    }
}
