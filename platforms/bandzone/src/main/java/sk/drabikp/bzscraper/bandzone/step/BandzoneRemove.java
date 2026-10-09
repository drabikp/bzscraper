package sk.drabikp.bzscraper.bandzone.step;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandzone.BandzoneUploadException;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.sync.domain.StepType;

/** Bandzone's remove: the concert is deleted. */
@Component
class BandzoneRemove extends BandzoneWithdrawal {

    public BandzoneRemove(BandzonePortalClient portalClient) {
        super(portalClient);
    }

    @Override
    public StepType type() {
        return StepType.REMOVE;
    }

    @Override
    void withdraw(BandzoneSession session, String bandzoneId) throws BandzoneUploadException {
        session.deleteGig(bandzoneId);
    }
}
