package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.GigId;

import java.util.Map;

/**
 * Which catalog gig each calendar event is, by event id. A link may point at a gig that
 * was deleted since; it then matches nothing.
 */
public interface CalendarLinkStore {

    Map<String, GigId> all();

    void link(String eventId, GigId gigId);

    void unlink(String eventId);

    /** Re-keys the links of a gig whose identity changed (an edit moved its date or venue). */
    void move(GigId from, GigId to);
}
