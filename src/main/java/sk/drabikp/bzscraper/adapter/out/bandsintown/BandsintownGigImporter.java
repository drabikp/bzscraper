package sk.drabikp.bzscraper.adapter.out.bandsintown;

import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.domain.model.ImportedGig;
import sk.drabikp.bzscraper.domain.model.Platform;

import java.util.List;

/**
 * Imports the artist's Bandsintown events (upcoming and past) through the artist portal
 * — the same logged-in, human-paced browser session publishing uses — each with its
 * Bandsintown event id.
 */
@Component
public class BandsintownGigImporter implements GigImporter {

    private final BitPortalClient portalClient;

    public BandsintownGigImporter(BitPortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDSINTOWN;
    }

    @Override
    public List<ImportedGig> importGigs() {
        try (BitSession session = portalClient.openSession()) {
            return session.listEvents();
        } catch (BitUploadException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
