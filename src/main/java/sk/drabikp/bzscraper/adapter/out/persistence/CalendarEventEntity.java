package sk.drabikp.bzscraper.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import sk.drabikp.bzscraper.domain.model.CalendarChange;
import sk.drabikp.bzscraper.domain.model.CalendarEvent;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.CalendarEventStatus;
import sk.drabikp.bzscraper.domain.model.KnownCalendarEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** JPA persistence model for one event of the saved calendar copy (table {@code calendar_event}). */
@Entity
@Table(name = "calendar_event")
public class CalendarEventEntity {

    @Id
    private String eventUid;
    private String title;
    private String location;
    @Column(length = 1_000_000)
    private String notes;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private boolean allDay;
    private boolean repeating;
    private boolean shownAsFree;
    private String calendarStatus;
    private Instant lastModified;
    private String suggested;
    private Instant firstSeen;
    private Instant lastSeen;
    private Instant removedAt;
    private String changeType;
    private String changeFields;
    private String suggestedBefore;
    private Instant changedAt;

    protected CalendarEventEntity() {
        // for JPA
    }

    CalendarEventEntity(String eventUid) {
        this.eventUid = eventUid;
    }

    /** Copies the domain state in; {@code lastSeen} is set when not null (a read). */
    void set(KnownCalendarEvent known, Instant seen) {
        CalendarEvent e = known.event();
        title = e.title();
        location = e.location();
        notes = e.notes();
        startsAt = e.start();
        endsAt = e.end();
        allDay = e.allDay();
        repeating = e.repeating();
        shownAsFree = e.shownAsFree();
        calendarStatus = e.calendarStatus().name();
        lastModified = e.lastModified();
        suggested = known.suggested().name();
        firstSeen = known.firstSeen();
        if (seen != null || lastSeen == null) {
            lastSeen = seen != null ? seen : known.firstSeen();
        }
        removedAt = known.removedAt();
        CalendarChange change = known.change();
        changeType = change == null ? null : change.type().name();
        changeFields = change == null ? null
                : change.fields().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        suggestedBefore = change == null || change.suggestedBefore() == null ? null : change.suggestedBefore().name();
        changedAt = change == null ? null : change.at();
    }

    KnownCalendarEvent toDomain() {
        CalendarEvent event = new CalendarEvent(eventUid, title, location, notes, startsAt, endsAt, allDay, repeating,
                shownAsFree, CalendarEventStatus.valueOf(calendarStatus), lastModified);
        CalendarChange change = changeType == null ? null : new CalendarChange(CalendarChange.Type.valueOf(changeType),
                changeFields == null || changeFields.isEmpty() ? Set.of() : Arrays.stream(changeFields.split(","))
                        .map(CalendarChange.Field::valueOf).collect(Collectors.toSet()),
                suggestedBefore == null ? null : CalendarEventKind.valueOf(suggestedBefore), changedAt);
        return new KnownCalendarEvent(event, CalendarEventKind.valueOf(suggested), firstSeen, removedAt, change);
    }

    String getEventUid() {
        return eventUid;
    }

    Instant getLastSeen() {
        return lastSeen;
    }
}
