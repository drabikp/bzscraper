package sk.drabikp.bzscraper.adapter.out.bandzone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sk.drabikp.bzscraper.application.port.out.GigImporter;
import sk.drabikp.bzscraper.application.port.out.GigProvider;
import sk.drabikp.bzscraper.domain.model.GigSummary;
import sk.drabikp.bzscraper.domain.model.ImportedGig;
import sk.drabikp.bzscraper.domain.model.Platform;
import sk.drabikp.bzscraper.domain.service.GigSummaryToGigMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Imports the configured band's Bandzone gigs (planned and played) by scraping its
 * public page. Each keeps its Bandzone concert id — the same id publishing records — so
 * importing links the catalog gig to the existing concert. Scraped gigs that can't form
 * a valid gig (no start/city) are dropped at the mapping boundary.
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
    public List<ImportedGig> importGigs() {
        if (bandSlug.isBlank()) {
            throw new IllegalStateException("Bandzone band slug not configured (bzscraper.bandzone.band-slug)");
        }
        List<ImportedGig> gigs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GigSummary summary : gigProvider.findByBand(bandSlug)) {
            if (summary.bzId() == null || summary.bzId().isBlank() || !seen.add(summary.bzId())) {
                continue;
            }
            GigSummaryToGigMapper.toGig(summary)
                    .ifPresent(gig -> gigs.add(new ImportedGig(Platform.BANDZONE, gig, summary.bzId())));
        }
        return gigs;
    }
}
