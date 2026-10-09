package sk.drabikp.bzscraper.calendar.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** JPA persistence model for the user's verdict on one calendar event. */
@Entity
@Table(name = "calendar_decision")
class CalendarDecisionEntity {

    @Id
    private String eventUid;
    private String verdict;
    private LocalDateTime decidedAt;

    protected CalendarDecisionEntity() {
        // for JPA
    }

    CalendarDecisionEntity(String eventUid, String verdict, LocalDateTime decidedAt) {
        this.eventUid = eventUid;
        this.verdict = verdict;
        this.decidedAt = decidedAt;
    }

    String getEventUid() {
        return eventUid;
    }

    String getVerdict() {
        return verdict;
    }
}
