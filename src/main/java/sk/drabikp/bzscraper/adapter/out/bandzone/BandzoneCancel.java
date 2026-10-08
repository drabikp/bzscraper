package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.domain.model.StepType;

/** Bandzone's cancel: the concert stays listed, marked cancelled. */
@Component
public class BandzoneCancel extends BandzoneWithdrawal {

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
