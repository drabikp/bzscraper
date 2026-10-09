package sk.drabikp.bzscraper.calendar.domain.event;

/** Whether a calendar event (a gig) is going ahead. */
public enum CalendarEventStatus {
    CONFIRMED,
    /** Not agreed yet ("PREDBEŽNE", "v jednaní") — may be linked, must not be published. */
    TENTATIVE,
    CANCELLED
}
