package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.application.port.out.CalendarLinkStore;
import sk.drabikp.bzscraper.domain.model.GigId;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/** JPA-backed {@link CalendarLinkStore}: table {@code calendar_link}. */
@Component
@Transactional
public class JpaCalendarLinkStore implements CalendarLinkStore {

    private final CalendarLinkJpaRepository jpaRepository;
    private final Clock clock;

    public JpaCalendarLinkStore(CalendarLinkJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, GigId> all() {
        return jpaRepository.findAll().stream().collect(Collectors.toMap(
                CalendarLinkEntity::getEventUid, l -> GigEntityMapper.deserializeId(l.getGigId())));
    }

    @Override
    public void link(String eventId, GigId gigId) {
        jpaRepository.save(new CalendarLinkEntity(eventId, GigEntityMapper.serializeId(gigId), clock.instant()));
    }

    @Override
    public void unlink(String eventId) {
        jpaRepository.deleteById(eventId);
    }

    @Override
    public void move(GigId from, GigId to) {
        if (from.equals(to)) {
            return;
        }
        for (CalendarLinkEntity link : jpaRepository.findByGigId(GigEntityMapper.serializeId(from))) {
            link.moveTo(GigEntityMapper.serializeId(to));
        }
    }
}
