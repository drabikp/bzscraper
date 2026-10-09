package sk.drabikp.bzscraper.catalog.application;

import sk.drabikp.bzscraper.catalog.application.port.in.GigBusyException;
import sk.drabikp.bzscraper.catalog.application.port.in.GigIdentityTakenException;
import sk.drabikp.bzscraper.catalog.application.port.in.GigWrites;
import sk.drabikp.bzscraper.catalog.application.port.out.GigMovedListener;
import sk.drabikp.bzscraper.gig.application.ConcurrentChangeException;
import sk.drabikp.bzscraper.gig.application.port.out.GigRepository;
import sk.drabikp.bzscraper.gig.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;
import sk.drabikp.bzscraper.sync.application.port.in.QueueSyncWork;
import sk.drabikp.bzscraper.sync.application.port.in.SyncStateUseCase;
import sk.drabikp.bzscraper.sync.domain.QueueResult;
import sk.drabikp.bzscraper.sync.domain.SyncTask;

import java.util.List;

/** The catalog's write path ({@link GigWrites}) over the repository and the sync outbox. */
public class CatalogWrites implements GigWrites {

    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final List<GigMovedListener> movedListeners;
    private final QueueSyncWork sync;
    private final SyncStateUseCase syncState;

    public CatalogWrites(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                         List<GigMovedListener> movedListeners, QueueSyncWork sync, SyncStateUseCase syncState) {
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.movedListeners = List.copyOf(movedListeners);
        this.sync = sync;
        this.syncState = syncState;
    }

    @Override
    public void add(Gig gig) {
        if (gigRepository.findById(gig.id()).isPresent()) {
            throw new GigIdentityTakenException(gig);
        }
        gigRepository.save(gig);
    }

    @Override
    public QueueResult replace(Gig seen, Gig updated) {
        GigId originalId = seen.id();
        Gig current = gigRepository.findById(originalId).orElseThrow(() ->
                new ConcurrentChangeException(ConcurrentChangeException.DELETED, "The gig was deleted meanwhile — nothing was changed."));
        if (!current.equals(seen)) {
            throw new ConcurrentChangeException(ConcurrentChangeException.CHANGED, "The gig was changed meanwhile (in another window, by an import or "
                    + "from the calendar) — nothing was changed. Reload it and edit again.");
        }
        requireNotBusy(current);
        if (!updated.id().equals(originalId)) {
            if (gigRepository.findById(updated.id()).isPresent()) {
                throw new GigIdentityTakenException(updated);
            }
            gigRepository.deleteById(originalId);
            publishedGigStore.move(originalId, updated.id());
            movedListeners.forEach(listener -> listener.gigMoved(originalId, updated.id()));
            sync.move(originalId, updated);
        }
        gigRepository.save(updated);
        return current.equals(updated) ? QueueResult.NOTHING : sync.update(updated);
    }

    @Override
    public void requireNotBusy(Gig gig) {
        if (syncState.busy(gig.id())) {
            throw new GigBusyException(SyncTask.labelOf(gig));
        }
    }

    @Override
    public void signal(QueueResult queued) {
        sync.signal(queued);
    }
}
