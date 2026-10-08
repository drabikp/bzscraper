package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.CancelGigUseCase;
import sk.drabikp.bzscraper.application.port.in.DeleteGigUseCase;
import sk.drabikp.bzscraper.application.port.in.ListGigsUseCase;
import sk.drabikp.bzscraper.application.port.in.ListPublicationsUseCase;
import sk.drabikp.bzscraper.application.port.in.SaveGigUseCase;
import sk.drabikp.bzscraper.application.port.in.UpdateGigUseCase;
import sk.drabikp.bzscraper.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.application.port.out.Transactions;
import sk.drabikp.bzscraper.domain.model.DateRange;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.Publication;
import sk.drabikp.bzscraper.domain.model.QueueResult;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the local gig catalog (the source of truth) over the {@link GigRepository}.
 * Thin application service — invariants live in the {@link Gig} aggregate, persistence
 * behind the repository port. Every change to a gig that is on a platform queues the
 * matching platform work in the sync outbox IN THE SAME TRANSACTION (see
 * {@link SyncRequests}), so the catalog and its pending platform work never disagree;
 * the platforms catch up when the sync worker runs. An edit that changes the gig's
 * identity also re-keys its published records, its pending tasks and its calendar links.
 */
public class GigCatalogService
        implements SaveGigUseCase, ListGigsUseCase, DeleteGigUseCase, UpdateGigUseCase, CancelGigUseCase,
        ListPublicationsUseCase {

    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final CalendarLinkStore calendarLinks;
    private final Transactions transactions;
    private final SyncRequests sync;

    public GigCatalogService(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                             CalendarLinkStore calendarLinks, Transactions transactions, SyncRequests sync) {
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.calendarLinks = calendarLinks;
        this.transactions = transactions;
        this.sync = sync;
    }

    @Override
    public void save(Gig gig) {
        gigRepository.save(gig);
    }

    @Override
    public List<Gig> allGigs() {
        return gigRepository.findAll();
    }

    @Override
    public List<Gig> gigsStartingWithin(DateRange range) {
        return gigRepository.findStartingWithin(range);
    }

    @Override
    public Map<GigId, Map<Platform, Publication>> publicationsByGig() {
        Map<GigId, Map<Platform, Publication>> byGig = new HashMap<>();
        for (Publication p : publishedGigStore.all()) {
            byGig.computeIfAbsent(p.gigId(), id -> new EnumMap<>(Platform.class)).put(p.platform(), p);
        }
        return byGig;
    }

    @Override
    public QueueResult delete(GigId id) {
        QueueResult queued = transactions.computeInTransaction(() -> {
            Gig gig = gigRepository.findById(id).orElse(null);
            gigRepository.deleteById(id);
            return sync.delete(id, gig);
        });
        sync.signal(queued);
        return queued;
    }

    @Override
    public QueueResult update(GigId originalId, Gig updated) {
        QueueResult queued = transactions.computeInTransaction(() -> {
            // If the edit moved the gig's identity (date/venue changed), drop the old row
            // and let the published records, pending platform work and calendar links follow.
            if (!updated.id().equals(originalId)) {
                gigRepository.deleteById(originalId);
                publishedGigStore.move(originalId, updated.id());
                calendarLinks.move(originalId, updated.id());
                sync.move(originalId, updated);
            }
            Optional<Gig> before = gigRepository.findById(updated.id());
            gigRepository.save(updated);
            boolean changed = before.map(b -> !b.equals(updated)).orElse(true);
            return changed ? sync.update(updated) : QueueResult.NOTHING;
        });
        sync.signal(queued);
        return queued;
    }

    @Override
    public QueueResult cancel(GigId id) {
        return lifecycle(id, true);
    }

    @Override
    public QueueResult reactivate(GigId id) {
        return lifecycle(id, false);
    }

    private QueueResult lifecycle(GigId id, boolean cancel) {
        QueueResult queued = transactions.computeInTransaction(() -> {
            Optional<Gig> stored = gigRepository.findById(id);
            if (stored.isEmpty() || stored.get().cancelled() == cancel) {
                return QueueResult.NOTHING;
            }
            Gig changed = cancel ? stored.get().cancel() : stored.get().reactivate();
            gigRepository.save(changed);
            return cancel ? sync.cancel(changed) : sync.reactivate(changed);
        });
        sync.signal(queued);
        return queued;
    }
}
