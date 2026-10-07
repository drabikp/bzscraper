package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ImportedGig;

import java.util.List;

/**
 * An authenticated Bandsintown artist-portal session. Events are addressed by their
 * Bandsintown event id. Bandsintown has no "cancelled" state: a cancelled gig is
 * removed, with "canceled" as the reason.
 */
public interface BitSession extends AutoCloseable {

    /**
     * Creates and publishes the gigs. Never throws for a single gig: each result says
     * whether that gig was published (with its new event id) or why not.
     *
     * @param notifyFollowers whether Bandsintown should notify the artist's followers
     * @return one result per gig, in input order
     */
    List<Created> createEvents(List<Gig> gigs, boolean notifyFollowers);

    /** Overwrites a published event's details in place; its id does not change. */
    void updateEvent(String eventId, Gig gig) throws BitUploadException;

    /**
     * Removes the event from Bandsintown. {@code cancelled} gives "the event was
     * canceled" as the reason. An event that is already gone is not an error.
     */
    void deleteEvent(String eventId, boolean cancelled) throws BitUploadException;

    /** Every event of the artist on Bandsintown — upcoming and past — with its event id. */
    List<ImportedGig> listEvents() throws BitUploadException;

    /** Releases the session (closes the browser). Never throws. */
    @Override
    void close();

    /** Outcome of creating one gig: its event id when published, otherwise the reason. */
    record Created(Gig gig, String eventId, String error) {

        public static Created published(Gig gig, String eventId) {
            return new Created(gig, eventId, null);
        }

        public static Created failed(Gig gig, String error) {
            return new Created(gig, null, error);
        }

        public boolean isPublished() {
            return eventId != null && error == null;
        }
    }
}
