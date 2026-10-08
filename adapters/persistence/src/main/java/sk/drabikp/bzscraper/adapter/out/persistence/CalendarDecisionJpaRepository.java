package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link CalendarDecisionEntity}; used only by {@link JpaCalendarDecisionStore}. */
public interface CalendarDecisionJpaRepository extends JpaRepository<CalendarDecisionEntity, String> {
}
