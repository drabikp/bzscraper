package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.GigProvider;
import sk.drabikp.bzscraper.domain.model.Gig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.service.GigSummaryToGigMapper;

import java.util.List;
import java.util.Optional;

/**
 * Imports gigs from Bandzone by scraping the configured band and mapping the scraped
 * read models to {@link Gig} aggregates. Scraped gigs that can't form a valid gig
 * (no start/city) are dropped at the mapping boundary.
 */
@Component
public class BandzoneGigImporter implements GigImporter {

    private final GigProvider gigProvider;
    private final String bandSlug;

    public BandzoneGigImporter(GigProvider gigProvider,
                               @Value("${bzscraper.bandzone.band-slug:}") String bandSlug) {
        this.gigProvider = gigProvider;
        this.bandSlug = bandSlug;
    }

    @Override
    public Platform platform() {
        return Platform.BANDZONE;
    }

    @Override
    public List<Gig> importGigs() {
        if (bandSlug.isBlank()) {
            throw new IllegalStateException("Bandzone band slug not configured (bzscraper.bandzone.band-slug)");
        }
        return gigProvider.findByBand(bandSlug).stream()
                .map(GigSummaryToGigMapper::toGig)
                .flatMap(Optional::stream)
                .toList();
    }
}
