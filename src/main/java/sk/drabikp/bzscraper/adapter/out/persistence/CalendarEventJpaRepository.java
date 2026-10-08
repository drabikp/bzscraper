package sk.drabikp.bzscraper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

/** Spring Data repository for {@link CalendarEventEntity}; used only by {@link JpaCalendarSnapshotStore}. */
public interface CalendarEventJpaRepository extends JpaRepository<CalendarEventEntity, String> {

    @Query("select max(e.lastSeen) from CalendarEventEntity e")
    Instant lastSeen();
}
