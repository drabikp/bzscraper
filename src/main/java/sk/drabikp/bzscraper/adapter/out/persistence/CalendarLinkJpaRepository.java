package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for {@link CalendarLinkEntity}; used only by {@link JpaCalendarLinkStore}. */
public interface CalendarLinkJpaRepository extends JpaRepository<CalendarLinkEntity, String> {

    List<CalendarLinkEntity> findByGigId(String gigId);
}
