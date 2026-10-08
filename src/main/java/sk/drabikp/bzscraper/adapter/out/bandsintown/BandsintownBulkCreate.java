package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepOutcome;
import sk.drabikp.bzscraper.domain.model.StepType;

import java.util.List;
import java.util.Optional;

/**
 * Bandsintown's bulk upload: one CSV for up to 25 gigs creates drafts, which are then
 * published (followers notified only when configured) and read back. Past events are taken
 * — they are listed under Past Events. A refused upload created nothing; an event that was
 * uploaded but isn't published exists as a draft, so it is left for the user.
 */
@Component
public class BandsintownBulkCreate implements SyncStep {

    private final BitPortalClient portalClient;
    private final boolean notifyFollowers;

    public BandsintownBulkCreate(BitPortalClient portalClient,
                                 @Value("${bzscraper.bandsintown.notify-followers:false}") boolean notifyFollowers) {
        this.portalClient = portalClient;
        this.notifyFollowers = notifyFollowers;
    }

    @Override
    public Platform platform() {
        return Platform.BANDSINTOWN;
    }

    @Override
    public StepType type() {
        return StepType.BULK_CREATE;
    }

    @Override
    public int batchSize() {
        return BandsintownBulkEdit.MAX_ROWS;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return Optional.empty();
    }

    @Override
    public List<StepOutcome> run(List<Item> items) {
        List<Gig> gigs = items.stream().map(Item::gig).toList();
        try (BitSession session = portalClient.openSession()) {
            List<BitSession.Created> created = session.createEvents(gigs, notifyFollowers);
            return created.stream().map(BandsintownBulkCreate::outcome).toList();
        } catch (BitUploadException e) {
            // a refused upload made nothing (Bandsintown's row errors); anything else may have
            StepOutcome outcome = e.permanent() ? StepOutcome.refused(e.getMessage()) : StepOutcome.failed(e.getMessage());
            return gigs.stream().map(gig -> outcome).toList();
        }
    }

    private static StepOutcome outcome(BitSession.Created created) {
        if (created.isPublished()) {
            return StepOutcome.created(created.eventId(), created.note());
        }
        if (created.error().startsWith("Bandsintown rejected this row")) {
            return StepOutcome.refused(created.error());                 // the row made nothing
        }
        return created.error().contains("Uploaded to Bandsintown as event")
                ? StepOutcome.failedForGood(created.error())             // a draft exists there
                : StepOutcome.failed(created.error());
    }
}
