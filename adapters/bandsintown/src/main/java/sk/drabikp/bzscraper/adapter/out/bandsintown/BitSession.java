package sk.drabikp.bzscraper.adapter.out.bandsintown;

import sk.drabikp.bzscraper.application.port.out.FailureKind;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.StepOutcome;
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

    /**
     * Overwrites several published events in one upload (at most 25), keyed by event id in
     * upload order. One result per event, in the same order; never throws for one event.
     *
     * @throws BitUploadException when the upload as a whole failed (nothing is known per event)
     */
    List<Edited> updateEvents(List<Map.Entry<String, Gig>> edits) throws BitUploadException;

    /**
     * Edits the event in the portal's single-page form — which also takes PAST events, unlike
     * the upload: the place is picked from Bandsintown's venue search in the gig's town.
     * Returns a note when Bandsintown placed it far from the town, else null.
     *
     * @throws BitUploadException permanent when Bandsintown refused it or has no such place
     */
    String editEventInForm(String eventId, Gig gig) throws BitUploadException;

    /** Removes the event from Bandsintown, for {@code reason}. An event that is already gone is not an error. */
    void deleteEvent(String eventId, RemovalReason reason) throws BitUploadException;

    /**
     * Removes the event through its single-page form — which also opens for PAST events, which
     * the event list can't remove. Same reasons as {@link #deleteEvent}; already gone is not an
     * error.
     */
    void deleteEventInForm(String eventId, RemovalReason reason) throws BitUploadException;

    /** Every event of the artist on Bandsintown — upcoming and past — with its event id. */
    List<ImportedGig> listEvents() throws BitUploadException;

    /** Releases the session (closes the browser). Never throws. */
    @Override
    void close();

    /**
     * Outcome of editing one event: updated, or why not and what that means — REFUSED
     * (Bandsintown's own "no" for the row, e.g. {@code INVALID_START_TIME}), TEMPORARY (the row
     * wasn't applied), NEEDS_USER (a new draft was made instead).
     */
    record Edited(String eventId, FailureKind failure, String error) {

        public static Edited updated(String eventId) {
            return new Edited(eventId, null, null);
        }

        public static Edited notUpdated(String eventId, FailureKind failure, String error) {
            return new Edited(eventId, failure, error);
        }

        public boolean isUpdated() {
            return failure == null;
        }

        public StepOutcome outcome() {
            return isUpdated() ? StepOutcome.done(null) : failure.outcome(error);
        }
    }

    /**
     * Outcome of creating one gig: its event id when published (with a {@code note} worth
     * showing, e.g. Bandsintown placed it far from the town), otherwise the reason and what it
     * means — REFUSED (the row made nothing), TEMPORARY, NEEDS_USER (a draft was left there).
     */
    record Created(Gig gig, String eventId, FailureKind failure, String error, String note) {

        public static Created published(Gig gig, String eventId) {
            return new Created(gig, eventId, null, null, null);
        }

        public static Created published(Gig gig, String eventId, String note) {
            return new Created(gig, eventId, null, null, note);
        }

        public static Created failed(Gig gig, FailureKind failure, String error) {
            return new Created(gig, null, failure, error, null);
        }

        public boolean isPublished() {
            return failure == null;
        }

        public StepOutcome outcome() {
            return isPublished() ? StepOutcome.created(eventId, note) : failure.outcome(error);
        }
    }
}
