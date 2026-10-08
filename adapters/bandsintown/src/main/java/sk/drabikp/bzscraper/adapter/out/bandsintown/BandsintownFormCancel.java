package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.domain.model.StepType;

import java.time.Clock;

/** Bandsintown's cancel through the event's form: removed as "canceled" — past events too. */
@Component
public class BandsintownFormCancel extends BandsintownRemoval {

    public BandsintownFormCancel(BitPortalClient portalClient, Clock clock) {
        super(portalClient, clock, RemovalReason.CANCELED, Way.EVENT_FORM);
    }

    @Override
    public StepType type() {
        return StepType.FORM_CANCEL;
    }
}
