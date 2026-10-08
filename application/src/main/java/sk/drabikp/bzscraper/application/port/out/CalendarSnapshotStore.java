package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.KnownCalendarEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The calendar as the app last read it ({@link KnownCalendarEvent}s by event id): what the
 * next read is compared with, and what the calendar page shows without reading again.
 * Holds the band's private notes — never published.
 */
public interface CalendarSnapshotStore {

    Map<String, KnownCalendarEvent> all();

    Optional<KnownCalendarEvent> find(String eventId);

    /** Stores the copy after a read made at {@code readAt}; events not in {@code events} are left as they are. */
    void saveRead(Collection<KnownCalendarEvent> events, Instant readAt);

    /** Stores one event's new state without counting as a read (e.g. a change marked seen). */
    void save(KnownCalendarEvent event);

    void remove(String eventId);

    /** When the calendar was last read, if ever. */
    Optional<Instant> lastRead();
}
