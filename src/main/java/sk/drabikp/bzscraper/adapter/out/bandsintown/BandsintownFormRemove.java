package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.domain.model.StepType;

import java.time.Clock;

/** Bandsintown's remove through the event's form (reason "other") — past events too. */
@Component
public class BandsintownFormRemove extends BandsintownRemoval {

    public BandsintownFormRemove(BitPortalClient portalClient, Clock clock) {
        super(portalClient, clock, false, true);
    }

    @Override
    public StepType type() {
        return StepType.FORM_REMOVE;
    }
}
