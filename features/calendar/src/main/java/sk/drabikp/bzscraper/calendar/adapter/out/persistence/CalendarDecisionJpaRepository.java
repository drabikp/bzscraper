package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link CalendarDecisionEntity}; used only by {@link JpaCalendarDecisionStore}. */
interface CalendarDecisionJpaRepository extends JpaRepository<CalendarDecisionEntity, String> {
}
