package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.OneAtATimeStep;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.StepOutcome;
import sk.drabikp.bzscraper.domain.model.StepType;

/**
 * Bandzone's form edit: the concert's update form, one concert at a time (Bandzone has no
 * bulk upload). Takes every gig, past ones included. An error Bandzone won't get over by
 * waiting (e.g. a city it doesn't know) goes to the user with what to correct.
 */
@Component
public class BandzoneFormEdit implements OneAtATimeStep {

    private final BandzonePortalClient portalClient;

    public BandzoneFormEdit(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return BandzonePlatform.PLATFORM;
    }

    @Override
    public StepType type() {
        return StepType.FORM_EDIT;
    }

    @Override
    public StepOutcome runOne(Item item) {
        try (BandzoneSession session = portalClient.openSession()) {
            session.updateGig(item.externalRef(), item.gig());
            return StepOutcome.done(null);
        } catch (BandzoneUploadException e) {
            return e.outcome();
        }
    }
}
