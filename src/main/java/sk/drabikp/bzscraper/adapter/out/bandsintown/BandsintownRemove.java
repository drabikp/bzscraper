package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.domain.model.StepType;

import java.time.Clock;

/** Bandsintown's remove: the event is deleted (reason "other"). */
@Component
public class BandsintownRemove extends BandsintownRemoval {

    public BandsintownRemove(BitPortalClient portalClient, Clock clock) {
        super(portalClient, clock, false);
    }

    @Override
    public StepType type() {
        return StepType.REMOVE;
    }
}
