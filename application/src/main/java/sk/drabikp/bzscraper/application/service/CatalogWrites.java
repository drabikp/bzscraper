package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.in.GigBusyException;
import sk.drabikp.bzscraper.application.port.in.GigIdentityTakenException;
import sk.drabikp.bzscraper.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.application.port.out.ConcurrentChangeException;
import sk.drabikp.bzscraper.application.port.out.GigRepository;
import sk.drabikp.bzscraper.application.port.out.PublishedGigStore;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.QueueResult;
import sk.drabikp.bzscraper.domain.model.SyncTask;

/**
 * The one way gigs are written to the catalog — the add page, edits, the calendar and Import
 * all go through here, so the catalog's rules hold for every writer:
 * <ul>
 *   <li>a gig never overwrites another: a new gig, or an edit that moves a gig's identity (its
 *       day or venue), onto an identity another gig has is refused;</li>
 *   <li>an edit applies only to the gig as the writer saw it (changed or deleted meanwhile →
 *       refused);</li>
 *   <li>a gig whose platform work is running is not changed;</li>
 *   <li>an identity move takes the gig's platform records, queued work and calendar links
 *       along; an edit queues the update on every platform the gig is on.</li>
 * </ul>
 * Call inside the caller's transaction; {@link #signal} once it committed.
 */
public class CatalogWrites {

    private final GigRepository gigRepository;
    private final PublishedGigStore publishedGigStore;
    private final CalendarLinkStore calendarLinks;
    private final SyncRequests sync;

    public CatalogWrites(GigRepository gigRepository, PublishedGigStore publishedGigStore,
                         CalendarLinkStore calendarLinks, SyncRequests sync) {
        this.gigRepository = gigRepository;
        this.publishedGigStore = publishedGigStore;
        this.calendarLinks = calendarLinks;
        this.sync = sync;
    }

    /** A new gig; refused when another gig already has its identity. */
    void add(Gig gig) {
        if (gigRepository.findById(gig.id()).isPresent()) {
            throw new GigIdentityTakenException(gig);
        }
        gigRepository.save(gig);
    }

    /** Replaces the gig {@code seen} with {@code updated} and queues the update where it is published. */
    QueueResult replace(Gig seen, Gig updated) {
        GigId originalId = seen.id();
        Gig current = gigRepository.findById(originalId).orElseThrow(() ->
                new ConcurrentChangeException("The gig was deleted meanwhile — nothing was changed."));
        if (!current.equals(seen)) {
            throw new ConcurrentChangeException("The gig was changed meanwhile (in another window, by an import or "
                    + "from the calendar) — nothing was changed. Reload it and edit again.");
        }
        requireNotBusy(current);
        if (!updated.id().equals(originalId)) {
            if (gigRepository.findById(updated.id()).isPresent()) {
                throw new GigIdentityTakenException(updated);
            }
            gigRepository.deleteById(originalId);
            publishedGigStore.move(originalId, updated.id());
            calendarLinks.move(originalId, updated.id());
            sync.move(originalId, updated);
        }
        gigRepository.save(updated);
        return current.equals(updated) ? QueueResult.NOTHING : sync.update(updated);
    }

    /** Refuses a gig whose platform work is running. */
    void requireNotBusy(Gig gig) {
        if (sync.running(gig.id())) {
            throw new GigBusyException(SyncTask.labelOf(gig));
        }
    }

    void signal(QueueResult queued) {
        sync.signal(queued);
    }
}
