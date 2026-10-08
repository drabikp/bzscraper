package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.application.port.out.CalendarSnapshotStore;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.GigId;
import sk.drabikp.bzscraper.domain.model.KnownCalendarEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory calendar stores for service tests. */
final class CalendarFakes {

    private CalendarFakes() {
    }

    static final class Links implements CalendarLinkStore {

        final Map<String, GigId> links = new HashMap<>();

        @Override
        public Map<String, GigId> all() {
            return Map.copyOf(links);
        }

        @Override
        public void link(String eventId, GigId gigId) {
            links.put(eventId, gigId);
        }

        @Override
        public void unlink(String eventId) {
            links.remove(eventId);
        }

        @Override
        public void move(GigId from, GigId to) {
            links.replaceAll((event, gig) -> gig.equals(from) ? to : gig);
        }
    }

    static final class Snapshots implements CalendarSnapshotStore {

        final Map<String, KnownCalendarEvent> events = new LinkedHashMap<>();
        Instant lastRead;

        @Override
        public Map<String, KnownCalendarEvent> all() {
            return new LinkedHashMap<>(events);
        }

        @Override
        public Optional<KnownCalendarEvent> find(String eventId) {
            return Optional.ofNullable(events.get(eventId));
        }

        @Override
        public void saveRead(Collection<KnownCalendarEvent> read, Instant readAt) {
            read.forEach(this::save);
            lastRead = readAt;
        }

        @Override
        public void save(KnownCalendarEvent event) {
            events.put(event.id(), event);
        }

        @Override
        public void remove(String eventId) {
            events.remove(eventId);
        }

        @Override
        public Optional<Instant> lastRead() {
            return Optional.ofNullable(lastRead);
        }
    }

    static final class Decisions implements CalendarDecisionStore {

        final Map<String, CalendarEventKind> verdicts = new HashMap<>();

        @Override
        public Map<String, CalendarEventKind> all() {
            return Map.copyOf(verdicts);
        }

        @Override
        public void decide(String eventId, CalendarEventKind verdict) {
            verdicts.put(eventId, verdict);
        }

        @Override
        public void forget(String eventId) {
            verdicts.remove(eventId);
        }
    }
}
