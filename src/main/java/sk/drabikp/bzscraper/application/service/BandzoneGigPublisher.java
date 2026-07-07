package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.BandzonePortalClient;
import sk.drabikp.bzscraper.application.port.out.BandzoneSession;
import sk.drabikp.bzscraper.application.port.out.BandzoneUploadException;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Bandzone push strategy: Bandzone has no bulk import, so each gig is created
 * individually via its 3-step wizard — but the whole batch shares ONE login via a
 * {@link BandzoneSession}. One gig failing does not stop the rest; a login failure
 * fails the whole batch. Idempotency and marking are handled by the orchestrator.
 */
public class BandzoneGigPublisher implements GigPublisher {

    private final BandzonePortalClient portalClient;

    public BandzoneGigPublisher(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDZONE;
    }

    @Override
    public List<PublishResult> publishNew(List<Gig> gigs) {
        List<PublishResult> results = new ArrayList<>();

        BandzoneSession session;
        try {
            session = portalClient.openSession();
        } catch (BandzoneUploadException e) {
            // Login failed → the whole batch fails; nothing is marked, so it retries.
            for (Gig gig : gigs) {
                results.add(PublishResult.failed(Platform.BANDZONE, gig, e.getMessage()));
            }
            return results;
        }

        try (session) {
            for (Gig gig : gigs) {
                try {
                    session.createGig(gig);
                    results.add(PublishResult.published(Platform.BANDZONE, gig));
                } catch (BandzoneUploadException e) {
                    results.add(PublishResult.failed(Platform.BANDZONE, gig, e.getMessage()));
                }
            }
        }
        return results;
    }
}
