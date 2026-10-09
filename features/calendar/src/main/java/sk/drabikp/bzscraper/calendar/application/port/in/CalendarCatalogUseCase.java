package sk.drabikp.bzscraper.calendar.application.port.in;

import sk.drabikp.bzscraper.gig.domain.Gig;
import sk.drabikp.bzscraper.gig.domain.GigId;

/**
 * Bringing the calendar's gigs into the catalog: add a gig from an event (the user checks
 * the pre-filled form), or link an event to the gig the catalog already has. A linked
 * event is how the calendar page tells the user when the calendar changes the gig.
 * Publishing stays a separate step in the catalog.
 */
public interface CalendarCatalogUseCase {

    /**
     * Saves {@code gig} to the catalog, linked to the event.
     *
     * @throws IllegalStateException if the catalog already has a gig with that identity
     */
    void addToCatalog(String eventId, Gig gig);

    void link(String eventId, GigId gigId);

    void unlink(String eventId);

    /** Links every gig event whose day has exactly one catalog gig not linked to another event; returns how many. */
    int linkSameDayGigs();
}
