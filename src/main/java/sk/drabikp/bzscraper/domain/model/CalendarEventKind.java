package sk.drabikp.bzscraper.domain.model;

/** What a calendar event is, for the band's gig admin. */
public enum CalendarEventKind {
    GIG,
    /** The rules can't tell — the user decides. Never a remembered verdict. */
    UNSURE,
    NOT_GIG
}
