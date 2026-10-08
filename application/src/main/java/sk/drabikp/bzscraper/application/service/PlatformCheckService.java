package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.CheckPlatformsUseCase;
import sk.drabikp.bzscraper.application.port.in.GigBusyException;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.PlatformException;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.SyncNotifier;
import sk.drabikp.bzscraper.application.port.out.SyncOutbox;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.Drift;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.ImportedGig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PlatformCheck;
import sk.drabikp.bzscraper.domain.model.Platforms;
import sk.drabikp.bzscraper.domain.model.Publication;
import sk.drabikp.bzscraper.domain.model.SyncStatus;
import sk.drabikp.bzscraper.domain.service.Reconciler;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Checks the platforms against the catalog ({@link Reconciler}) for the upcoming gigs and
 * those of the last {@code pastDays}, reading each platform through its importer — the same
 * read-only reading as Import. A platform held back by its circuit breaker
 * is not read; one that can't be read is reported as such. One check at a time; the latest
 * result is kept in memory (a restart forgets it — the next check finds the same again).
 */
public class PlatformCheckService implements CheckPlatformsUseCase {

    private final List<GigImporter> importers;
    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final SyncOutbox outbox;
    private final Transactions transactions;
    private final PlatformHealth health;
    private final Platforms platforms;
    private final SyncNotifier notifier;
    private final Clock clock;
    private final int pastDays;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile PlatformCheck last;

    public PlatformCheckService(List<GigImporter> importers, GigRepository gigRepository,
                                PublishedGigStore publishedGigStore, SyncOutbox outbox, Transactions transactions,
                                PlatformHealth health, Platforms platforms, SyncNotifier notifier, Clock clock,
                                int pastDays) {
        this.importers = importers;
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.outbox = outbox;
        this.transactions = transactions;
        this.health = health;
        this.platforms = platforms;
        this.notifier = notifier;
        this.clock = clock;
        this.pastDays = pastDays;
    }

    @Override
    public Optional<PlatformCheck> check() {
        if (!running.compareAndSet(false, true)) {
            return Optional.empty();
        }
        notifier.changed();
        try {
            List<Drift> drifts = new ArrayList<>();
            Map<Platform, String> unreadable = new TreeMap<>();
            Map<Platform, Integer> unlinked = new TreeMap<>();
            for (GigImporter importer : importers) {
                Platform platform = importer.platform();
                if (health.held(platform)) {
                    unreadable.put(platform, "held back after failing again and again — not read this time");
                    continue;
                }
                List<ImportedGig> listed;
                try {
                    listed = importer.importGigs();
                } catch (PlatformException e) {
                    unreadable.put(platform, e.getMessage());
                    continue;
                } catch (RuntimeException e) {
                    unreadable.put(platform, PlatformReads.unexpected(platform, e));
                    continue;
                }
                // the catalog as it is once the platform has been read (that read takes a while):
                // upcoming gigs and the recent past — older history isn't worth reconciling
                LocalDate since = LocalDate.now(clock).minusDays(pastDays);
                List<Gig> catalog = gigRepository.findAll().stream()
                        .filter(g -> !g.schedule().start().toLocalDate().isBefore(since)).toList();
                List<Publication> records = publishedGigStore.all();
                Set<GigId> syncing = outbox.unfinished().stream().filter(t -> t.status().open())
                        .map(t -> t.gigId()).collect(Collectors.toSet());
                drifts.addAll(Reconciler.drifts(platforms.traits(platform), catalog, records, listed, syncing));
                unlinked.put(platform, Reconciler.unlinked(platform, records, listed));
            }
            last = new PlatformCheck(clock.instant(), drifts, unreadable, unlinked);
            return Optional.of(last);
        } finally {
            running.set(false);
            notifier.changed();
        }
    }

    @Override
    public Optional<PlatformCheck> lastCheck() {
        return Optional.ofNullable(last);
    }

    @Override
    public boolean running() {
        return running.get();
    }

    @Override
    public void forget(Platform platform, GigId gigId) {
        transactions.inTransaction(() -> {
            if (outbox.openFor(gigId).stream().anyMatch(t -> t.status() == SyncStatus.RUNNING)) {
                throw new GigBusyException(gigId.toString());
            }
            publishedGigStore.remove(platform, gigId);
        });
        dismiss(platform, gigId);
    }

    @Override
    public synchronized void dismiss(Platform platform, GigId gigId) {
        PlatformCheck check = last;
        if (check != null) {
            last = new PlatformCheck(check.checkedAt(), check.drifts().stream()
                    .filter(d -> d.platform() != platform || !d.gigId().equals(gigId)).toList(),
                    check.unreadable(), check.unlinked());
        }
        notifier.changed();
    }
}
