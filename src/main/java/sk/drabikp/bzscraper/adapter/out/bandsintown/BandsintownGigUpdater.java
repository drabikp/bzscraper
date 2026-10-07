package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.GigUpdateException;
import sk.drabikp.bzscraper.application.port.out.GigUpdater;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;

/**
 * Edits a Bandsintown event in place by its id. A cancelled gig is skipped: cancelling
 * removed it from Bandsintown, so there is nothing left to edit.
 */
@Component
public class BandsintownGigUpdater implements GigUpdater {

    private final BitPortalClient portalClient;

    public BandsintownGigUpdater(BitPortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDSINTOWN;
    }

    @Override
    public void update(String externalRef, Gig gig) throws GigUpdateException {
        if (gig.cancelled()) {
            return;
        }
        try (BitSession session = portalClient.openSession()) {
            session.updateEvent(externalRef, gig);
        } catch (BitUploadException e) {
            throw new GigUpdateException(e.getMessage(), e);
        }
    }
}
