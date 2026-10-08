package sk.drabikp.bzscraper.application.port.out;

import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.ImportedGig;

import java.util.List;
import java.util.Map;

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
     * Overwrites several published events in one upload (at most 25), keyed by event id in
     * upload order. One result per event, in the same order; never throws for one event.
     *
     * @throws BitUploadException when the upload as a whole failed (nothing is known per event)
     */
    List<Edited> updateEvents(List<Map.Entry<String, Gig>> edits) throws BitUploadException;

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

    /**
     * Outcome of editing one event: {@code refusal} is Bandsintown's own "no" for that row
     * ({@code INVALID_START_TIME}); {@code error} anything else (the row wasn't applied, or a
     * new draft was made instead); both null when it was updated.
     */
    record Edited(String eventId, String refusal, String error) {

        public boolean updated() {
            return refusal == null && error == null;
        }
    }

    /**
     * Outcome of creating one gig: its event id when published (with a {@code note} worth
     * showing, e.g. Bandsintown placed it far from the town), otherwise the reason.
     */
    record Created(Gig gig, String eventId, String error, String note) {

        public static Created published(Gig gig, String eventId) {
            return new Created(gig, eventId, null, null);
        }

        public static Created published(Gig gig, String eventId, String note) {
            return new Created(gig, eventId, null, note);
        }

        public static Created failed(Gig gig, String error) {
            return new Created(gig, null, error, null);
        }

        public boolean isPublished() {
            return eventId != null && error == null;
        }
    }
}
