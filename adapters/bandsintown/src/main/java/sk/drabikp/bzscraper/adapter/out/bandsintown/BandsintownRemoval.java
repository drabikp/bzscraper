package sk.drabikp.bzscraper.adapter.out.bandsintown;

import sk.drabikp.bzscraper.application.port.out.OneAtATimeStep;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepOutcome;

import java.time.Clock;
import java.util.Optional;

/**
 * Removes Bandsintown events, with a reason. Bandsintown has no cancelled state, so cancelling
 * removes the event too ("the event was canceled"). Two ways: the Upcoming list's "⋯ →
 * Delete" — which doesn't take past events (they are only listed under Past Events) — and,
 * after it, the event's own form (opened by id; past events too). One event at a time;
 * already gone = done.
 */
abstract class BandsintownRemoval implements OneAtATimeStep {

    /** Where the event is removed: its row in the Upcoming list (upcoming events only) or its own form. */
    enum Way { EVENT_LIST, EVENT_FORM }

    private final BitPortalClient portalClient;
    private final Clock clock;
    private final RemovalReason reason;
    private final Way way;

    BandsintownRemoval(BitPortalClient portalClient, Clock clock, RemovalReason reason, Way way) {
        this.portalClient = portalClient;
        this.clock = clock;
        this.reason = reason;
        this.way = way;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return way == Way.EVENT_LIST && gig != null && gig.isPast(clock)
                ? Optional.of("Bandsintown's event list doesn't remove past events")
                : Optional.empty();
    }

    @Override
    public StepOutcome runOne(Item item) {
        try (BitSession session = portalClient.openSession()) {
            if (way == Way.EVENT_FORM) {
                session.deleteEventInForm(item.externalRef(), reason);
            } else {
                session.deleteEvent(item.externalRef(), reason);
            }
            return StepOutcome.done(reason == RemovalReason.CANCELED
                    ? "removed (Bandsintown has no cancelled state)" : null);
        } catch (BitUploadException e) {
            return e.outcome();
        }
    }
}
