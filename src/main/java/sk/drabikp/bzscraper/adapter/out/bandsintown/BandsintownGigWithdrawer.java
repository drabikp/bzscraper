package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

/**
 * Withdraws a Bandsintown event by its id. Bandsintown has no cancelled state, so both
 * actions remove the event; a cancel gives "the event was canceled" as the reason.
 */
@Component
public class BandsintownGigWithdrawer implements GigWithdrawer {

    private final BitPortalClient portalClient;

    public BandsintownGigWithdrawer(BitPortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDSINTOWN;
    }

    @Override
    public void withdraw(String externalRef, WithdrawAction action) throws GigWithdrawalException {
        try (BitSession session = portalClient.openSession()) {
            session.deleteEvent(externalRef, action == WithdrawAction.CANCEL);
        } catch (BitUploadException e) {
            throw new GigWithdrawalException(e.getMessage(), e);
        }
    }
}
