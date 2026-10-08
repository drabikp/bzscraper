package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.application.port.out.CalendarSnapshotStore;
import sk.drabikp.bzscraper.domain.model.KnownCalendarEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** JPA-backed {@link CalendarSnapshotStore}: table {@code calendar_event}. */
@Component
@Transactional
public class JpaCalendarSnapshotStore implements CalendarSnapshotStore {

    private final CalendarEventJpaRepository jpaRepository;

    public JpaCalendarSnapshotStore(CalendarEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, KnownCalendarEvent> all() {
        return jpaRepository.findAll().stream().map(CalendarEventEntity::toDomain)
                .collect(Collectors.toMap(KnownCalendarEvent::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnownCalendarEvent> find(String eventId) {
        return jpaRepository.findById(eventId).map(CalendarEventEntity::toDomain);
    }

    @Override
    public void saveRead(Collection<KnownCalendarEvent> events, Instant readAt) {
        Map<String, CalendarEventEntity> stored = jpaRepository.findAllById(
                        events.stream().map(KnownCalendarEvent::id).toList()).stream()
                .collect(Collectors.toMap(CalendarEventEntity::getEventUid, Function.identity()));
        List<CalendarEventEntity> changed = events.stream().map(known -> {
            CalendarEventEntity entity = stored.getOrDefault(known.id(), new CalendarEventEntity(known.id()));
            entity.set(known, known.removed() ? null : readAt);
            return entity;
        }).toList();
        jpaRepository.saveAll(changed);
    }

    @Override
    public void save(KnownCalendarEvent event) {
        CalendarEventEntity entity = jpaRepository.findById(event.id()).orElseGet(() -> new CalendarEventEntity(event.id()));
        entity.set(event, null);
        jpaRepository.save(entity);
    }

    @Override
    public void remove(String eventId) {
        jpaRepository.deleteById(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> lastRead() {
        return Optional.ofNullable(jpaRepository.lastSeen());
    }
}
