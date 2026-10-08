package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.CalendarEventKind;

import java.util.Map;

/** The user's verdicts on calendar events (gig / not a gig), by event id, kept forever. */
public interface CalendarDecisionStore {

    Map<String, CalendarEventKind> all();

    void decide(String eventId, CalendarEventKind verdict);

    void forget(String eventId);
}
