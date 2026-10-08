package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.OneAtATimeStep;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepOutcome;
import sk.drabikp.bzscraper.domain.model.StepType;

/**
 * Bandsintown's form edit: the event's single-page edit form, one event at a time — what
 * the bulk upload can't do: past events (the upload refuses them) and rows it refused.
 * Sets the place (from Bandsintown's venue search, in the gig's town), dates, times, name
 * and description. A cancelled gig needs nothing: cancelling removed it.
 */
@Component
public class BandsintownFormEdit implements OneAtATimeStep {

    private final BitPortalClient portalClient;

    public BandsintownFormEdit(BitPortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return BandsintownPlatform.PLATFORM;
    }

    @Override
    public StepType type() {
        return StepType.FORM_EDIT;
    }

    @Override
    public StepOutcome runOne(Item item) {
        if (item.gig().cancelled()) {
            return StepOutcome.done("cancelled — Bandsintown keeps no cancelled copy to edit");
        }
        try (BitSession session = portalClient.openSession()) {
            return StepOutcome.done(session.editEventInForm(item.externalRef(), item.gig()));
        } catch (BitUploadException e) {
            return e.outcome();
        }
    }
}
