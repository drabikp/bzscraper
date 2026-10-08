package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.drabikp.bzscraper.application.port.out.CalendarDecisionStore;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/** JPA-backed {@link CalendarDecisionStore}: table {@code calendar_decision}. */
@Component
@Transactional
public class JpaCalendarDecisionStore implements CalendarDecisionStore {

    private final CalendarDecisionJpaRepository jpaRepository;
    private final Clock clock;

    public JpaCalendarDecisionStore(CalendarDecisionJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, CalendarEventKind> all() {
        return jpaRepository.findAll().stream().collect(Collectors.toMap(
                CalendarDecisionEntity::getEventUid, d -> CalendarEventKind.valueOf(d.getVerdict())));
    }

    @Override
    public void decide(String eventId, CalendarEventKind verdict) {
        jpaRepository.save(new CalendarDecisionEntity(eventId, verdict.name(), LocalDateTime.now(clock)));
    }

    @Override
    public void forget(String eventId) {
        jpaRepository.deleteById(eventId);
    }
}
