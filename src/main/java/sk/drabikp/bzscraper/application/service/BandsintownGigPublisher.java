package sk.drabikp.bzscraper.application.service;

import sk.drabikp.bzscraper.application.port.out.BitPortalClient;
import sk.drabikp.bzscraper.application.port.out.BitUploadException;
import sk.drabikp.bzscraper.application.port.out.GigCsvExporter;
import sk.drabikp.bzscraper.application.port.out.GigPublisher;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.model.PublishResult;

import java.util.List;

/**
 * Bandsintown push strategy: builds one bulk-import CSV for the whole batch and
 * uploads it through the artist portal. All-or-nothing — the CSV upload either
 * succeeds (every gig PUBLISHED) or fails (every gig FAILED). Idempotency and
 * marking are handled by the orchestrator, not here.
 */
public class BandsintownGigPublisher implements GigPublisher {

    private final GigCsvExporter csvExporter;
    private final BitPortalClient portalClient;

    public BandsintownGigPublisher(GigCsvExporter csvExporter, BitPortalClient portalClient) {
        this.csvExporter = csvExporter;
        this.portalClient = portalClient;
    }

    @Override
    public Platform platform() {
        return Platform.BANDSINTOWN;
    }

    @Override
    public List<PublishResult> publishNew(List<Gig> gigs) {
        String csv = csvExporter.export(gigs);
        try {
            portalClient.uploadCsv(csv);
        } catch (BitUploadException e) {
            return gigs.stream()
                    .map(gig -> PublishResult.failed(Platform.BANDSINTOWN, gig, e.getMessage()))
                    .toList();
        }
        return gigs.stream()
                .map(gig -> PublishResult.published(Platform.BANDSINTOWN, gig))
                .toList();
    }
}
