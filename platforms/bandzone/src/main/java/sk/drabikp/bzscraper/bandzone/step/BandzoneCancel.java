package sk.drabikp.bzscraper.bandzone.step;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.bandzone.BandzoneUploadException;
import sk.drabikp.bzscraper.bandzone.portal.BandzonePortalClient;
import sk.drabikp.bzscraper.bandzone.portal.BandzoneSession;
import sk.drabikp.bzscraper.sync.domain.StepType;

/** Bandzone's cancel: the concert stays listed, marked cancelled. */
@Component
class BandzoneCancel extends BandzoneWithdrawal {

    public BandzoneCancel(BandzonePortalClient portalClient) {
        super(portalClient);
    }

    @Override
    public StepType type() {
        return StepType.CANCEL;
    }

    @Override
    void withdraw(BandzoneSession session, String bandzoneId) throws BandzoneUploadException {
        session.cancelGig(bandzoneId);
    }
}
