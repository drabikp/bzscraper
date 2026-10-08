package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.application.port.out.SyncStep;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepOutcome;
import sk.drabikp.bzscraper.domain.model.StepType;

import java.util.List;
import java.util.Optional;

/**
 * Bandzone's create: the 2-step wizard takes only the basics, so each new concert is then
 * completed through the edit form (end, venue, poster, lineup). One gig at a time — each
 * new concert is recorded before the next is made. Once created, the gig is done even if
 * completing it fails (with a note to Re-sync): failing it would make it twice.
 */
@Component
public class BandzoneFormCreate implements SyncStep {

    private final BandzonePortalClient portalClient;

    public BandzoneFormCreate(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDZONE;
    }

    @Override
    public StepType type() {
        return StepType.FORM_CREATE;
    }

    @Override
    public int batchSize() {
        return 1;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return Optional.empty();                 // Bandzone keeps past concerts too
    }

    @Override
    public List<StepOutcome> run(List<Item> items) {
        return items.stream().map(item -> create(item.gig())).toList();
    }

    private StepOutcome create(Gig gig) {
        try (BandzoneSession session = portalClient.openSession()) {
            String bandzoneId = session.createGig(gig);
            try {
                session.updateGig(bandzoneId, gig);
                return StepOutcome.created(bandzoneId, null);
            } catch (BandzoneUploadException e) {
                return StepOutcome.created(bandzoneId,
                        "created, but not all details were saved (" + e.getMessage() + ") — use Re-sync");
            }
        } catch (BandzoneUploadException e) {
            // a permanent error (e.g. a town Bandzone doesn't know) stopped it before creating
            return e.permanent() ? StepOutcome.refused(e.getMessage()) : StepOutcome.failed(e.getMessage());
        }
    }
}
