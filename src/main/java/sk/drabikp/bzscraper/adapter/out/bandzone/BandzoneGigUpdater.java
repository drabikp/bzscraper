package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.application.port.out.GigUpdateException;
import sk.drabikp.bzscraper.application.port.out.GigUpdater;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;

/**
 * Edits a Bandzone concert in place by its id, driving the concert's update form
 * through an authenticated {@link BandzoneSession} (the same login used to publish).
 */
@Component
public class BandzoneGigUpdater implements GigUpdater {

    private final BandzonePortalClient portalClient;

    public BandzoneGigUpdater(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDZONE;
    }

    @Override
    public void update(String externalRef, Gig gig) throws GigUpdateException {
        BandzoneSession session;
        try {
            session = portalClient.openSession();
        } catch (BandzoneUploadException e) {
            throw new GigUpdateException("Bandzone login failed: " + e.getMessage(), e, e.permanent());
        }
        try (session) {
            session.updateGig(externalRef, gig);
        } catch (BandzoneUploadException e) {
            throw new GigUpdateException(e.getMessage(), e, e.permanent());
        }
    }
}
