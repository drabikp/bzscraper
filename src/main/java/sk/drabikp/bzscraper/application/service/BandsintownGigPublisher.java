package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitSession;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;

import java.util.List;

/**
 * Bandsintown push strategy: creates and publishes the batch through one
 * {@link BitSession} (one login) and returns each gig's Bandsintown event id, so the
 * gig can later be edited or removed there. Followers are notified only when
 * configured to. Idempotency and recording are handled by the orchestrator, not here.
 */
public class BandsintownGigPublisher implements GigPublisher {

    private final BitPortalClient portalClient;
    private final boolean notifyFollowers;

    public BandsintownGigPublisher(BitPortalClient portalClient, boolean notifyFollowers) {
        this.portalClient = portalClient;
        this.notifyFollowers = notifyFollowers;
    }

    @Override
    public Platform platform() {
        return Platform.BANDSINTOWN;
    }

    @Override
    public List<PublishResult> publishNew(List<Gig> gigs) {
        if (gigs.isEmpty()) {
            return List.of();
        }
        try (BitSession session = portalClient.openSession()) {
            return session.createEvents(gigs, notifyFollowers).stream()
                    .map(c -> c.isPublished()
                            ? PublishResult.published(Platform.BANDSINTOWN, c.gig(), c.eventId())
                            : PublishResult.failed(Platform.BANDSINTOWN, c.gig(), c.error()))
                    .toList();
        } catch (BitUploadException e) {
            return gigs.stream()
                    .map(gig -> PublishResult.failed(Platform.BANDSINTOWN, gig, e.getMessage()))
                    .toList();
        }
    }
}
