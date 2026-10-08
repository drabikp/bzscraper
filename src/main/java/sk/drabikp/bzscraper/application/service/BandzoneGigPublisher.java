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
 * individually via its 2-step wizard — but the whole batch shares ONE login via a
 * {@link BandzoneSession}. The wizard only takes the basics, so each created gig is then
 * completed through the edit form (end, venue, poster, lineup). One gig failing does not
 * stop the rest; a login failure fails the whole batch. Idempotency and marking are
 * handled by the orchestrator.
 */
public class BandzoneGigPublisher implements GigPublisher {

    private final BandzonePortalClient portalClient;

    public BandzoneGigPublisher(BandzonePortalClient portalClient) {
        this.portalClient = portalClient;
    }

    @Override
    public boolean publishesPastEvents() {
        return true;                        // Bandzone keeps the band's past concerts
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
                    String bandzoneId = session.createGig(gig);
                    results.add(complete(session, bandzoneId, gig));
                } catch (BandzoneUploadException e) {
                    results.add(PublishResult.failed(Platform.BANDZONE, gig, e.getMessage()));
                }
            }
        }
        return results;
    }

    /**
     * The gig exists on Bandzone once created, so it is PUBLISHED (and recorded) even if
     * completing its details fails — a FAILED result would make the next run post it twice.
     */
    private static PublishResult complete(BandzoneSession session, String bandzoneId, Gig gig) {
        try {
            session.updateGig(bandzoneId, gig);
            return PublishResult.published(Platform.BANDZONE, gig, bandzoneId);
        } catch (BandzoneUploadException e) {
            return PublishResult.publishedWithNote(Platform.BANDZONE, gig, bandzoneId,
                    "created, but not all details were saved (" + e.getMessage() + ") — use Re-sync");
        }
    }
}
