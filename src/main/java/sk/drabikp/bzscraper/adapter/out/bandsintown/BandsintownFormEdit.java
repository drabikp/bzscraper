package sk.drabikp.bzscraper.adapter.out.bandsintown;

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
 * Bandsintown's form edit: the event's single-page edit form, one event at a time — what
 * the bulk upload can't do: past events (the upload refuses them) and rows it refused.
 * Sets the place (from Bandsintown's venue search, in the gig's town), dates, times, name
 * and description. A cancelled gig needs nothing: cancelling removed it.
 */
@Component
public class BandsintownFormEdit implements SyncStep {

    private final BitPortalClient portalClient;

    public BandsintownFormEdit(BitPortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDSINTOWN;
    }

    @Override
    public StepType type() {
        return StepType.FORM_EDIT;
    }

    @Override
    public int batchSize() {
        return 1;
    }

    @Override
    public Optional<String> refusal(Gig gig) {
        return Optional.empty();
    }

    @Override
    public List<StepOutcome> run(List<Item> items) {
        return items.stream().map(this::edit).toList();
    }

    private StepOutcome edit(Item item) {
        if (item.gig().cancelled()) {
            return StepOutcome.done("cancelled — Bandsintown keeps no cancelled copy to edit");
        }
        try (BitSession session = portalClient.openSession()) {
            return StepOutcome.done(session.editEventInForm(item.externalRef(), item.gig()));
        } catch (BitUploadException e) {
            return e.permanent() ? StepOutcome.refused(e.getMessage()) : StepOutcome.failed(e.getMessage());
        }
    }
}
