package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.domain.model.StepType;

import java.time.Clock;

/** Bandsintown's remove through the event's form (reason "other") — past events too. */
@Component
public class BandsintownFormRemove extends BandsintownRemoval {

    public BandsintownFormRemove(BitPortalClient portalClient, Clock clock) {
        super(portalClient, clock, RemovalReason.OTHER, Way.EVENT_FORM);
    }

    @Override
    public StepType type() {
        return StepType.FORM_REMOVE;
    }
}
