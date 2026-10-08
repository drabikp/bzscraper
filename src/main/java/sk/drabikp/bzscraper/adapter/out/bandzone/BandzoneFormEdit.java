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
 * Bandzone's form edit: the concert's update form, one concert at a time (Bandzone has no
 * bulk upload). Takes every gig, past ones included. An error Bandzone won't get over by
 * waiting (e.g. a city it doesn't know) goes to the user with what to correct.
 */
@Component
public class BandzoneFormEdit implements SyncStep {

    private final BandzonePortalClient portalClient;

    public BandzoneFormEdit(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDZONE;
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
        try (BandzoneSession session = portalClient.openSession()) {
            session.updateGig(item.externalRef(), item.gig());
            return StepOutcome.done(null);
        } catch (BandzoneUploadException e) {
            return e.permanent() ? StepOutcome.failedForGood(e.getMessage()) : StepOutcome.failed(e.getMessage());
        }
    }
}
