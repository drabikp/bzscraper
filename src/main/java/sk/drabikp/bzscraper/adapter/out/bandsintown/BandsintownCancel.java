package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.domain.model.StepType;

import java.time.Clock;

/** Bandsintown's cancel: it has no cancelled state, so the event is removed as "canceled". */
@Component
public class BandsintownCancel extends BandsintownRemoval {

    public BandsintownCancel(BitPortalClient portalClient, Clock clock) {
        super(portalClient, clock, true, false);
    }

    @Override
    public StepType type() {
        return StepType.CANCEL;
    }
}
