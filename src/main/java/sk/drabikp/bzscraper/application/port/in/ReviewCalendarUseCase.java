package sk.drabikp.bzscraper.application.port.in;

import sk.drabikp.bzscraper.application.port.out.CalendarUnavailableException;
import sk.drabikp.bzscraper.domain.model.CalendarClassification;
import sk.drabikp.bzscraper.domain.model.CalendarEvent;
import sk.drabikp.bzscraper.domain.model.CalendarEventKind;
import sk.drabikp.bzscraper.domain.model.ProfileRule;

import java.util.List;

/**
 * Sorting the band calendar's events into gigs and everything else, with the band's
 * profile, and remembering the user's answers. Reading and classifying are separate so
 * a verdict re-classifies without reading the calendar again.
 */
public interface ReviewCalendarUseCase {

    boolean configured();

    List<CalendarEvent> read() throws CalendarUnavailableException;

    List<CalendarClassification> classify(List<CalendarEvent> events);

    /** Remembers the user's verdict — {@code GIG} or {@code NOT_GIG} — for the event. */
    void decide(String eventId, CalendarEventKind verdict);

    /** Forgets the verdict; the profile decides again. */
    void forget(String eventId);

    List<ProfileRule> rules();
}
