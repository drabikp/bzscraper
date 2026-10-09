package sk.drabikp.bzscraper.bandsintown.step;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandsintown.RemovalReason;
import sk.drabikp.bzscraper.bandsintown.portal.BitPortalClient;
import sk.drabikp.bzscraper.sync.domain.StepType;

import java.time.Clock;

/** Bandsintown's remove: the event is deleted (reason "other"). */
@Component
class BandsintownRemove extends BandsintownRemoval {

    public BandsintownRemove(BitPortalClient portalClient, Clock clock) {
        super(portalClient, clock, RemovalReason.OTHER, Way.EVENT_LIST);
    }

    @Override
    public StepType type() {
        return StepType.REMOVE;
    }
}
