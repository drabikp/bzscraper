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
 * Removes Bandsintown events through the Upcoming list's "⋯ → Delete", with a reason.
 * Bandsintown has no cancelled state, so cancelling removes the event too ("the event was
 * canceled"). A past event is only listed under Past Events, where this doesn't go: it is
 * not taken (delete it there by hand). One event at a time; already gone = done.
 */
abstract class BandsintownRemoval implements SyncStep {

    private final BitPortalClient portalClient;
    private final Clock clock;
    private final boolean cancelled;

    BandsintownRemoval(BitPortalClient portalClient, Clock clock, boolean cancelled) {
        this.portalClient = portalClient;
        this.clock = clock;
        this.cancelled = cancelled;
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
        return gig != null && gig.isPast(clock)
                ? Optional.of("Bandsintown's event list doesn't remove past events — delete it there by hand "
                + "(Past Events)")
                : Optional.empty();
    }

    @Override
    public List<StepOutcome> run(List<Item> items) {
        return items.stream().map(this::remove).toList();
    }

    private StepOutcome remove(Item item) {
        try (BitSession session = portalClient.openSession()) {
            session.deleteEvent(item.externalRef(), cancelled);
            return StepOutcome.done(cancelled ? "removed (Bandsintown has no cancelled state)" : null);
        } catch (BitUploadException e) {
            return e.permanent() ? StepOutcome.refused(e.getMessage()) : StepOutcome.failed(e.getMessage());
        }
    }
}
