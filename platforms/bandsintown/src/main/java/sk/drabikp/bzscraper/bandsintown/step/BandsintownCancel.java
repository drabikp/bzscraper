package sk.drabikp.bzscraper.bandsintown.step;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandsintown.RemovalReason;
import sk.drabikp.bzscraper.bandsintown.portal.BitPortalClient;
import sk.drabikp.bzscraper.sync.domain.StepType;

import java.time.Clock;

/** Bandsintown's cancel: it has no cancelled state, so the event is removed as "canceled". */
@Component
class BandsintownCancel extends BandsintownRemoval {

    public BandsintownCancel(BitPortalClient portalClient, Clock clock) {
        super(portalClient, clock, RemovalReason.CANCELED, Way.EVENT_LIST);
    }

    @Override
    public StepType type() {
        return StepType.CANCEL;
    }
}
