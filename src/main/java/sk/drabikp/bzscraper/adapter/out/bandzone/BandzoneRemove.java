package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.domain.model.StepType;

/** Bandzone's remove: the concert is deleted. */
@Component
public class BandzoneRemove extends BandzoneWithdrawal {

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
