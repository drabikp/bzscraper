package sk.drabikp.bzscraper.adapter.out.bandsintown;

import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepOutcome;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Removes Bandsintown events, with a reason. Bandsintown has no cancelled state, so cancelling
 * removes the event too ("the event was canceled"). Two ways: the Upcoming list's "⋯ →
 * Delete" — which doesn't take past events (they are only listed under Past Events) — and,
 * after it, the event's own form (opened by id; past events too). One event at a time;
 * already gone = done.
 */
abstract class BandsintownRemoval implements SyncStep {

    private final BitPortalClient portalClient;
    private final Clock clock;
    private final boolean cancelled;
    private final boolean viaForm;

    BandsintownRemoval(BitPortalClient portalClient, Clock clock, boolean cancelled, boolean viaForm) {
        this.portalClient = portalClient;
        this.clock = clock;
        this.cancelled = cancelled;
        this.viaForm = viaForm;
    }

    @Override
    public Platform platform() {
        return Platform.BANDSINTOWN;
    }

    @Override
    public int batchSize() {
        return 1;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return !viaForm && gig != null && gig.isPast(clock)
                ? Optional.of("Bandsintown's event list doesn't remove past events")
                : Optional.empty();
    }

    @Override
    public List<StepOutcome> run(List<Item> items) {
        return items.stream().map(this::remove).toList();
    }

    private StepOutcome remove(Item item) {
        try (BitSession session = portalClient.openSession()) {
            if (viaForm) {
                session.deleteEventInForm(item.externalRef(), cancelled);
            } else {
                session.deleteEvent(item.externalRef(), cancelled);
            }
            return StepOutcome.done(cancelled ? "removed (Bandsintown has no cancelled state)" : null);
        } catch (BitUploadException e) {
            return e.permanent() ? StepOutcome.refused(e.getMessage()) : StepOutcome.failed(e.getMessage());
        }
    }
}
