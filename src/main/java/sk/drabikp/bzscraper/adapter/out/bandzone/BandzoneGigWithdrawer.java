package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawalException;
import sk.drabikp.bzscraper.application.port.out.GigWithdrawer;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.WithdrawAction;

/**
 * Cancels or deletes a Bandzone concert by its id, driving the band-admin delete tab
 * through an authenticated {@link BandzoneSession} (the same login used to publish).
 */
@Component
public class BandzoneGigWithdrawer implements GigWithdrawer {

    private final BandzonePortalClient portalClient;

    public BandzoneGigWithdrawer(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDZONE;
    }

    @Override
    public void withdraw(String externalRef, WithdrawAction action) throws GigWithdrawalException {
        BandzoneSession session;
        try {
            session = portalClient.openSession();
        } catch (BandzoneUploadException e) {
            throw new GigWithdrawalException("Bandzone login failed: " + e.getMessage(), e);
        }
        try (session) {
            switch (action) {
                case CANCEL -> session.cancelGig(externalRef);
                case DELETE -> session.deleteGig(externalRef);
            }
        } catch (BandzoneUploadException e) {
            throw new GigWithdrawalException(e.getMessage(), e);
        }
    }
}
