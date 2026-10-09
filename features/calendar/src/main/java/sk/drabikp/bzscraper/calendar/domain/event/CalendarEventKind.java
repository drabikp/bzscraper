package sk.drabikp.bzscraper.calendar.domain.event;

/** What a calendar event is, for the band's gig admin. */
public enum CalendarEventKind {
    GIG,
    /** The rules can't tell — the user decides. Never a remembered verdict. */
    UNSURE,
    NOT_GIG
}
