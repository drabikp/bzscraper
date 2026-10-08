package sk.drabikp.bzscraper.domain.model;

/** Whether a calendar event (a gig) is going ahead. */
public enum CalendarEventStatus {
    CONFIRMED,
    /** Not agreed yet ("PREDBEŽNE", "v jednaní") — may be linked, must not be published. */
    TENTATIVE,
    CANCELLED
}
