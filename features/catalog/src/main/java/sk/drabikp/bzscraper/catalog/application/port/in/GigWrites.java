package sk.drabikp.bzscraper.catalog.application.port.in;

import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.sync.domain.QueueResult;

/**
 * The one way gigs are written to the catalog — the add page, edits, the calendar and Import
 * all go through here, so the catalog's rules hold for every writer:
 * <ul>
 *   <li>a gig never overwrites another: a new gig, or an edit that moves a gig's identity (its
 *       day or venue), onto an identity another gig has is refused ({@link GigIdentityTakenException});</li>
 *   <li>an edit applies only to the gig as the writer saw it (changed or deleted meanwhile →
 *       {@code ConcurrentChangeException});</li>
 *   <li>a gig whose platform work is running is not changed ({@link GigBusyException});</li>
 *   <li>an identity move takes the gig's platform records and queued work along and tells
 *       the {@code GigMovedListener}s (e.g. the calendar's links); an edit queues the update on
 *       every platform the gig is on.</li>
 * </ul>
 * Call inside the caller's transaction; {@link #signal} once it committed.
 */
public interface GigWrites {

    /** A new gig; refused when another gig already has its identity. */
    void add(Gig gig);

    /** Replaces the gig {@code seen} with {@code updated} and queues the update where it is published. */
    QueueResult replace(Gig seen, Gig updated);

    /** Refuses a gig whose platform work is running. */
    void requireNotBusy(Gig gig);

    /** Tells the sync about the queued work. */
    void signal(QueueResult queued);
}
